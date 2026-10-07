package filter;

import java.awt.image.BufferedImage;

public interface FilterStrategy {
    
    /**
     * Menerapkan filter ke gambar original.
     * @param original Gambar asli dari kamera
     * @return Gambar baru yang sudah difilter
     */
    BufferedImage applyFilter(BufferedImage original);
    
    String getFilterName();
}