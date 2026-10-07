package export;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * Strategi export ke file PNG lokal. Pemilihan lokasi file dilakukan oleh pemanggil (GUI),
 * sehingga strategi ini aman dijalankan di thread background.
 */
public class LocalExportStrategy implements ExportStrategy {

    private final File target;

    public LocalExportStrategy(File target) {
        if (target == null) throw new IllegalArgumentException("target tidak boleh null");
        this.target = withPngExtension(target);
    }

    static File withPngExtension(File file) {
        return file.getName().toLowerCase().endsWith(".png") ? file : new File(file.getPath() + ".png");
    }

    public File getTarget() {
        return target;
    }

    @Override
    public String getStrategyName() { return "Komputer"; }

    @Override
    public boolean export(BufferedImage image, File videoFile) {
        System.out.println("LOG: Menjalankan strategi Ekspor Lokal...");
        if (image == null) return false;
        try {
            File parent = target.getAbsoluteFile().getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                return false;
            }
            // Video sudah tersimpan di folder video oleh StripVideoExporter
            boolean written = ImageIO.write(image, "PNG", target);
            if (written) {
                System.out.println("SUKSES: Gambar disimpan ke: " + target.getAbsolutePath());
            }
            return written;
        } catch (IOException e) {
            System.err.println("ERROR: " + e.getMessage());
            return false;
        }
    }
}
