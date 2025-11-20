package export;

import java.awt.image.BufferedImage;

// Ini adalah "Strategy Interface" (Menggantikan PaymentStrategy)
public interface ExportStrategy {
    
    /**
     * Menjalankan strategi ekspor untuk gambar yang diberikan.
     * @param image Gambar final yang akan diekspor
     * @return boolean true jika sukses, false jika gagal.
     */
    boolean export(BufferedImage image);
    
    String getStrategyName();
}