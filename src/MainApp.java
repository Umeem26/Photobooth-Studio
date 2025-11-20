import service.PhotoboothService;
// --- MODIFIKASI: Import GUI yang baru ---
import gui.TemplateSelectionGUI; 
import javax.swing.SwingUtilities;

// 1. IMPORT LIBRARY FLATLAF
import com.formdev.flatlaf.FlatDarkLaf;

/**
 * Titik masuk utama aplikasi (Main class).
 * VERSI BARU: Meluncurkan halaman pemilihan template.
 */
public class MainApp {

    public static void main(String[] args) {
        
        // 2. AKTIFKAN TEMA FLATLAF (DARK MODE)
        try {
            FlatDarkLaf.setup();
        } catch (Exception ex) {
            System.err.println("Gagal menginisialisasi FlatLaf: " + ex.getMessage());
        }

        System.out.println("LOG: Meluncurkan Aplikasi Photobooth...");
        
        // 3. Siapkan "otak" aplikasi
        // Service ini akan dioper ke semua jendela
        PhotoboothService service = new PhotoboothService();

        // 4. Jalankan GUI di Thread Swing
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                // --- MODIFIKASI: Luncurkan TEMPLATE SELECTION GUI ---
                // Buat GUI pemilihan dan oper service ke dalamnya
                TemplateSelectionGUI selectionGUI = new TemplateSelectionGUI(service);
                
                // Tampilkan GUI pemilihan
                selectionGUI.setVisible(true);
                // --- BATAS MODIFIKASI ---
            }
        });
    }
}