package export;

import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;

// Ini adalah "Concrete Strategy"
// VERSI BARU: Mengatur folder default untuk "Save As"
public class LocalExportStrategy implements ExportStrategy {

    @Override
    public String getStrategyName() { return "Simpan ke Komputer"; }

    @Override
    public boolean export(BufferedImage image) {
        System.out.println("LOG: Menjalankan strategi Ekspor Lokal...");

        // --- MODIFIKASI DIMULAI DI SINI ---
        
        // 1. Tentukan path ke folder default
        // "../" berarti "satu folder di atas" dari folder tempat aplikasi berjalan
        String defaultPath = "../HasilPhotobooth";
        File defaultDir = new File(defaultPath);

        // 2. Jika folder "HasilPhotobooth" belum ada, buatlah
        if (!defaultDir.exists()) {
            System.out.println("LOG: Folder 'HasilPhotobooth' tidak ditemukan, membuat folder baru...");
            defaultDir.mkdirs(); // Buat foldernya
        }

        // 3. Atur JFileChooser untuk memulai di folder tersebut
        JFileChooser fileChooser = new JFileChooser(defaultDir);
        // --- BATAS MODIFIKASI ---
        
        fileChooser.setDialogTitle("Simpan Strip Foto");
        fileChooser.setFileFilter(new FileNameExtensionFilter("PNG Image", "png"));
        
        // Set nama file default
        fileChooser.setSelectedFile(new File("photobooth_strip.png"));

        // Tampilkan dialog "Save"
        int userSelection = fileChooser.showSaveDialog(null); // 'null' agar di tengah layar

        if (userSelection == JFileChooser.APPROVE_OPTION) {
            File fileToSave = fileChooser.getSelectedFile();
            
            // Pastikan file memiliki ekstensi .png
            if (!fileToSave.getAbsolutePath().endsWith(".png")) {
                fileToSave = new File(fileToSave.getAbsolutePath() + ".png");
            }
            
            try {
                // Tulis gambar ke file yang dipilih
                ImageIO.write(image, "PNG", fileToSave);
                System.out.println("SUKSES: Gambar disimpan ke: " + fileToSave.getAbsolutePath());
                return true;
            } catch (Exception e) {
                System.err.println("ERROR: Gagal menyimpan file: " + e.getMessage());
                return false;
            }
        } else {
            System.out.println("LOG: Pengguna membatalkan penyimpanan.");
            return false;
        }
    }
}