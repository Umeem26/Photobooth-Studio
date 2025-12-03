package export;

import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;

public class LocalExportStrategy implements ExportStrategy {

    @Override
    public String getStrategyName() { return "Komputer"; }

    // Update signature method agar sesuai interface
    @Override
    public boolean export(BufferedImage image, File videoFile) {
        System.out.println("LOG: Menjalankan strategi Ekspor Lokal...");

        String defaultPath = "../HasilPhotobooth";
        File defaultDir = new File(defaultPath);
        if (!defaultDir.exists()) defaultDir.mkdirs();

        JFileChooser fileChooser = new JFileChooser(defaultDir);
        fileChooser.setDialogTitle("Simpan Strip Foto");
        fileChooser.setFileFilter(new FileNameExtensionFilter("PNG Image", "png"));
        fileChooser.setSelectedFile(new File("photobooth_strip.png"));

        int userSelection = fileChooser.showSaveDialog(null);

        if (userSelection == JFileChooser.APPROVE_OPTION) {
            File fileToSave = fileChooser.getSelectedFile();
            if (!fileToSave.getAbsolutePath().endsWith(".png")) {
                fileToSave = new File(fileToSave.getAbsolutePath() + ".png");
            }
            try {
                ImageIO.write(image, "PNG", fileToSave);
                System.out.println("SUKSES: Gambar disimpan ke: " + fileToSave.getAbsolutePath());
                
                // Video sudah otomatis tersimpan di folder Video oleh Exporter
                // Jadi kita tidak perlu memindahkannya secara manual di sini untuk versi Lokal
                
                return true;
            } catch (Exception e) {
                System.err.println("ERROR: " + e.getMessage());
                return false;
            }
        }
        return false;
    }
}