package print;

import config.AppConfig;
import config.ConfigStore;
import exception.BoothException;
import exception.BoothException.Kind;
import template.BrandedStripTemplate;
import template.StripLayout;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import javax.imageio.ImageIO;
import javax.print.PrintService;
import javax.print.attribute.standard.PrinterIsAcceptingJobs;

/**
 * Antrian cetak async per sesi dengan batas print.maxCopies. Status dibaca lewat polling:
 * queued, printing, done, failed (idle bila belum pernah mencetak).
 */
public class PrintManager implements AutoCloseable {

    public static final String TEST_KEY = "__test__";

    public enum State {
        IDLE, QUEUED, PRINTING, DONE, FAILED;

        public String id() {
            return name().toLowerCase();
        }
    }

    public record JobStatus(State state, int copiesUsed, int maxCopies, String error) { }

    public record PrinterInfo(String name, boolean isDefault, String status) { }

    private static final class Job {
        State state = State.IDLE;
        int copiesUsed;
        String error;
    }

    private final ConfigStore configStore;
    private final PrinterResolver resolver;
    private final ExecutorService executor;
    private final Map<String, Job> jobs = new ConcurrentHashMap<>();

    public PrintManager(ConfigStore configStore, PrinterResolver resolver, ExecutorService executor) {
        this.configStore = configStore;
        this.resolver = resolver;
        this.executor = executor;
    }

    public List<PrinterInfo> printers() {
        String def = resolver.defaultService().map(PrintService::getName).orElse(null);
        List<PrinterInfo> out = new ArrayList<>();
        for (PrintService s : resolver.services()) {
            out.add(new PrinterInfo(s.getName(), s.getName().equals(def), status(s)));
        }
        return out;
    }

    private static String status(PrintService s) {
        PrinterIsAcceptingJobs accepting = s.getAttribute(PrinterIsAcceptingJobs.class);
        if (accepting == null) return "unknown";
        return accepting == PrinterIsAcceptingJobs.ACCEPTING_JOBS ? "ready" : "not accepting jobs";
    }

    public JobStatus status(String key) {
        Job job = jobs.get(key);
        int max = configStore.current().printMaxCopies();
        if (job == null) return new JobStatus(State.IDLE, 0, max, null);
        synchronized (job) {
            return new JobStatus(job.state, job.copiesUsed, max, job.error);
        }
    }

    /** Mengantre cetak strip sesi. 409 bila sedang mencetak atau melebihi print.maxCopies. */
    public JobStatus submit(String key, Path stripPng, int copies) throws BoothException {
        AppConfig c = configStore.current();
        if (copies < 1) throw new BoothException(Kind.BAD_REQUEST, "copies minimal 1");
        Job job = jobs.computeIfAbsent(key, k -> new Job());
        synchronized (job) {
            if (job.state == State.QUEUED || job.state == State.PRINTING) {
                throw new BoothException(Kind.CONFLICT, "Masih mencetak");
            }
            if (!TEST_KEY.equals(key) && job.copiesUsed + copies > c.printMaxCopies()) {
                throw new BoothException(Kind.CONFLICT, "Batas cetak tercapai");
            }
            job.copiesUsed += copies;
            job.state = State.QUEUED;
            job.error = null;
        }
        PrintExportStrategy strategy = new PrintExportStrategy(resolver, c.printPrinter(), copies, c.printTwoUp(),
                c.printToFile() ? c.printQueueDir() : null, "vandebooth_" + key);
        executor.execute(() -> run(key, job, strategy, stripPng, copies));
        return status(key);
    }

    /** Halaman uji untuk Mode Operator (tidak dihitung ke batas salinan). */
    public JobStatus printTest(Path tempDir) throws BoothException {
        try {
            BufferedImage sample = new BrandedStripTemplate(StripLayout.VERTICAL_4, "Test page")
                    .applyTemplate(new ArrayList<>());
            Files.createDirectories(tempDir);
            Path file = tempDir.resolve("print-test.png");
            ImageIO.write(sample, "png", file.toFile());
            return submit(TEST_KEY, file, 1);
        } catch (IOException e) {
            throw new BoothException(Kind.CONFLICT, "Halaman uji gagal dibuat");
        }
    }

    private void run(String key, Job job, PrintExportStrategy strategy, Path stripPng, int copies) {
        synchronized (job) {
            job.state = State.PRINTING;
        }
        boolean ok;
        String error;
        try {
            BufferedImage strip = ImageIO.read(stripPng.toFile());
            ok = strategy.export(strip, null);
            error = strategy.lastError();
        } catch (IOException | RuntimeException e) {
            ok = false;
            error = e.getMessage();
        }
        synchronized (job) {
            job.state = ok ? State.DONE : State.FAILED;
            job.error = ok ? null : error;
            if (!ok && !TEST_KEY.equals(key)) job.copiesUsed -= copies; // salinan gagal tidak dihitung
        }
    }

    public void forget(String key) {
        jobs.remove(key);
    }

    @Override
    public void close() {
        executor.shutdown();
    }
}
