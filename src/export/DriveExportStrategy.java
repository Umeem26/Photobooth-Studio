package export;

import java.awt.image.BufferedImage;

// Ini adalah "Concrete Strategy" (Menggantikan QrisPayment)
// Untuk saat ini, ini hanya placeholder (dummy)
public class DriveExportStrategy implements ExportStrategy {

    @Override
    public String getStrategyName() { return "Upload ke Google Drive"; }

    @Override
    public boolean export(BufferedImage image) {
        System.out.println("LOG: Menjalankan strategi Ekspor ke Drive...");
        
        // --- TODO: Logika Upload Google Drive ---
        // Logika ini kompleks dan memerlukan Google API Client Library.
        // Untuk Tugas Besar ini, kita cukup simulasikan saja.
        System.out.println("SIMULASI: Mengunggah gambar ke Google Drive...");
        try {
            Thread.sleep(1500); // Simulasi waktu upload
        } catch (InterruptedException e) {
            // abaikan
        }
        
        System.out.println("SUKSES: Gambar berhasil diunggah (simulasi). URL: http://drive.google.com/link-foto-anda");
        
        return true; 
    }
}