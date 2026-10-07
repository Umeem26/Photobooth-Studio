package service;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import exception.ExportFailedException;
import exception.TemplateNotFoundException;
import export.ExportStrategy;
import hardware.Camera;
import repository.SessionRepository;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

class PhotoboothServiceAsyncTest {

    private static final String TEMPLATE = "TPL-V-4";

    @TempDir
    Path tmp;

    private ExecutorService executor;
    private PhotoboothService service;
    private final List<Integer> percents = new CopyOnWriteArrayList<>();
    private final List<String> threads = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() {
        executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "test-worker"));
        Camera camera = new Camera() {
            @Override public BufferedImage capture() { return frame(); }
            @Override public void close() { }
        };
        service = new PhotoboothService(camera, new SessionRepository(tmp.resolve("sessions")), executor);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        service.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }

    private static BufferedImage frame() {
        return new BufferedImage(160, 120, BufferedImage.TYPE_INT_RGB);
    }

    private ProgressListener recorder() {
        return (percent, message) -> {
            percents.add(percent);
            threads.add(Thread.currentThread().getName());
        };
    }

    private void captureFour() throws Exception {
        for (int i = 0; i < 4; i++) service.addCapturedImage(service.captureImage());
    }

    @Test
    void generateStripAsyncRunsOnWorkerAndReportsProgress() throws Exception {
        captureFour();

        BufferedImage strip = service.generateStripAsync(TEMPLATE, recorder()).get(10, TimeUnit.SECONDS);

        assertNotNull(strip);
        assertEquals(List.of(0, 100), percents);
        assertTrue(threads.stream().allMatch("test-worker"::equals));
    }

    @Test
    void generateStripAsyncUsesSnapshotOfCapturedImages() throws Exception {
        captureFour();
        var future = service.generateStripAsync(TEMPLATE, null);
        service.clearCapturedImages();
        assertNotNull(future.get(10, TimeUnit.SECONDS));
    }

    @Test
    void generateStripAsyncFailsForUnknownTemplate() {
        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> service.generateStripAsync("TPL-Gaib", null).get(10, TimeUnit.SECONDS));
        assertInstanceOf(TemplateNotFoundException.class, ex.getCause());
    }

    @Test
    void exportAsyncArchivesSessionThenExports() throws Exception {
        captureFour();
        BufferedImage strip = service.generateStrip(TEMPLATE);
        File video = Files.createFile(tmp.resolve("strip.mp4")).toFile();
        RecordingStrategy strategy = new RecordingStrategy(true);

        Path sessionDir = service.exportAsync(TEMPLATE, strip, strategy, () -> video, recorder())
                .get(10, TimeUnit.SECONDS);

        assertTrue(sessionDir.startsWith(tmp.resolve("sessions")));
        assertTrue(Files.isRegularFile(sessionDir.resolve("strip.png")));
        assertTrue(Files.isRegularFile(sessionDir.resolve("frame_4.png")));
        assertTrue(Files.isRegularFile(sessionDir.resolve("meta.properties")));
        assertSame(strip, strategy.image);
        assertSame(video, strategy.video);
        assertEquals(List.of(0, 20, 50, 80, 100), percents);
        assertTrue(threads.stream().allMatch("test-worker"::equals));
    }

    @Test
    void exportAsyncContinuesWhenVideoTaskFails() throws Exception {
        captureFour();
        RecordingStrategy strategy = new RecordingStrategy(true);

        Path sessionDir = service.exportAsync(TEMPLATE, service.generateStrip(TEMPLATE), strategy,
                () -> { throw new IllegalStateException("encoder rusak"); }, null).get(10, TimeUnit.SECONDS);

        assertTrue(Files.isDirectory(sessionDir));
        assertNull(strategy.video);
        assertNotNull(strategy.image);
    }

    @Test
    void exportAsyncFailsWithExportFailedExceptionWhenStrategyFails() throws Exception {
        captureFour();
        BufferedImage strip = service.generateStrip(TEMPLATE);

        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> service.exportAsync(TEMPLATE, strip, new RecordingStrategy(false), null, null)
                        .get(10, TimeUnit.SECONDS));
        assertInstanceOf(ExportFailedException.class, ex.getCause());
    }

    @Test
    void exportAsyncWithoutStrategyOnlyArchives() throws Exception {
        captureFour();
        Path sessionDir = service.exportAsync(TEMPLATE, service.generateStrip(TEMPLATE), null, null, null)
                .get(10, TimeUnit.SECONDS);
        assertTrue(Files.isRegularFile(sessionDir.resolve("strip.png")));
    }

    static final class RecordingStrategy implements ExportStrategy {
        private final boolean result;
        volatile BufferedImage image;
        volatile File video;

        RecordingStrategy(boolean result) { this.result = result; }

        @Override public boolean export(BufferedImage image, File videoFile) {
            this.image = image;
            this.video = videoFile;
            return result;
        }

        @Override public String getStrategyName() { return "Rekaman"; }
    }
}
