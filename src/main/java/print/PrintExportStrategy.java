package print;

import export.ExportStrategy;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import javax.imageio.ImageIO;
import javax.print.DocFlavor;
import javax.print.DocPrintJob;
import javax.print.PrintException;
import javax.print.PrintService;
import javax.print.SimpleDoc;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.Copies;
import javax.print.attribute.standard.JobName;
import javax.print.attribute.standard.MediaSize;
import javax.print.attribute.standard.MediaSizeName;
import javax.print.attribute.standard.OrientationRequested;

/**
 * Strategi export ke-2: cetak strip lewat javax.print pada kertas 4x6 (single atau two-up).
 * Mode file (print.mode=file) menulis PNG siap cetak ke folder print-queue tanpa printer.
 */
public class PrintExportStrategy implements ExportStrategy {

    private final PrinterResolver resolver;
    private final String printerName;
    private final int copies;
    private final boolean twoUp;
    private final Path fileQueueDir; // null = cetak ke printer
    private final String jobName;
    private String lastError;

    public PrintExportStrategy(PrinterResolver resolver, String printerName, int copies, boolean twoUp,
                               Path fileQueueDir, String jobName) {
        if (copies < 1) throw new IllegalArgumentException("copies minimal 1");
        this.resolver = resolver;
        this.printerName = printerName == null ? "" : printerName;
        this.copies = copies;
        this.twoUp = twoUp;
        this.fileQueueDir = fileQueueDir;
        this.jobName = jobName;
    }

    @Override
    public String getStrategyName() {
        return fileQueueDir != null ? "Print (file)" : "Print";
    }

    /** Alasan kegagalan terakhir (untuk status/log), atau null. */
    public String lastError() {
        return lastError;
    }

    @Override
    public boolean export(BufferedImage image, File videoFile) {
        lastError = null;
        if (image == null) {
            lastError = "Tidak ada gambar";
            return false;
        }
        BufferedImage page = PrintPageComposer.compose(image, twoUp);
        return fileQueueDir != null ? writeFiles(page) : printToService(page);
    }

    private boolean writeFiles(BufferedImage page) {
        try {
            Files.createDirectories(fileQueueDir);
            for (int i = 1; i <= copies; i++) {
                File target = fileQueueDir.resolve(jobName + "_" + System.currentTimeMillis() + "_" + i + ".png").toFile();
                if (!ImageIO.write(page, "png", target)) {
                    lastError = "Writer PNG tidak tersedia";
                    return false;
                }
            }
            return true;
        } catch (IOException e) {
            lastError = e.getMessage();
            return false;
        }
    }

    private boolean printToService(BufferedImage page) {
        Optional<PrintService> service = resolver.find(printerName);
        if (service.isEmpty()) {
            lastError = printerName.isEmpty() ? "Tidak ada printer default" : "Printer tidak ditemukan: " + printerName;
            return false;
        }
        PrintService ps = service.get();
        DocFlavor flavor = DocFlavor.SERVICE_FORMATTED.PRINTABLE;
        PrintRequestAttributeSet attrs = new HashPrintRequestAttributeSet();
        attrs.add(new Copies(copies));
        attrs.add(new JobName(jobName, null));
        attrs.add(page.getHeight() >= page.getWidth() ? OrientationRequested.PORTRAIT : OrientationRequested.LANDSCAPE);
        MediaSizeName media = MediaSize.findMedia(4f, 6f, MediaSize.INCH);
        if (media != null && ps.isAttributeValueSupported(media, flavor, attrs)) {
            attrs.add(media);
        }
        DocPrintJob job = ps.createPrintJob();
        try {
            job.print(new SimpleDoc(new PagePrintable(page), flavor, null), attrs);
            return true;
        } catch (PrintException | RuntimeException e) {
            lastError = "Cetak gagal: " + e.getMessage();
            return false;
        }
    }

    /** Menggambar halaman ke area cetak printer dengan rasio tetap. */
    static final class PagePrintable implements Printable {
        private final BufferedImage page;

        PagePrintable(BufferedImage page) {
            this.page = page;
        }

        BufferedImage page() {
            return page;
        }

        @Override
        public int print(Graphics graphics, PageFormat format, int pageIndex) {
            if (pageIndex > 0) return NO_SUCH_PAGE;
            Graphics2D g = (Graphics2D) graphics;
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            Rectangle area = new Rectangle((int) format.getImageableX(), (int) format.getImageableY(),
                    (int) format.getImageableWidth(), (int) format.getImageableHeight());
            Rectangle r = PrintPageComposer.fit(page.getWidth(), page.getHeight(), area);
            g.drawImage(page, r.x, r.y, r.width, r.height, null);
            return PAGE_EXISTS;
        }
    }
}
