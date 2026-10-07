package filter;

import java.awt.image.BufferedImage;

/**
 * Filter hangat: menaikkan kanal merah (dan sedikit hijau), menurunkan biru.
 * Tidak mengubah gambar asli.
 */
public class WarmFilterStrategy implements FilterStrategy {

    static final int RED_BOOST = 22;
    static final int GREEN_BOOST = 8;
    static final int BLUE_CUT = 22;

    @Override
    public String getFilterName() {
        return "Warm";
    }

    @Override
    public BufferedImage applyFilter(BufferedImage original) {
        int width = original.getWidth();
        int height = original.getHeight();
        BufferedImage warm = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        int[] row = new int[width];
        for (int y = 0; y < height; y++) {
            original.getRGB(0, y, width, 1, row, 0, width);
            for (int x = 0; x < width; x++) {
                int rgb = row[x];
                int r = clamp(((rgb >> 16) & 0xFF) + RED_BOOST);
                int g = clamp(((rgb >> 8) & 0xFF) + GREEN_BOOST);
                int b = clamp((rgb & 0xFF) - BLUE_CUT);
                row[x] = (r << 16) | (g << 8) | b;
            }
            warm.setRGB(0, y, width, 1, row, 0, width);
        }
        return warm;
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : Math.min(255, v);
    }
}
