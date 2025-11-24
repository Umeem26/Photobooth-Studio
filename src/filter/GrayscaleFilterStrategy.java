package filter;

import java.awt.image.BufferedImage;

public class GrayscaleFilterStrategy implements FilterStrategy {

    @Override
    public String getFilterName() {
        return "Grayscale";
    }

    @Override
    public BufferedImage applyFilter(BufferedImage original) {
        int width = original.getWidth();
        int height = original.getHeight();

        BufferedImage grayImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = original.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = (rgb & 0xFF);

                int gray = (int) (0.299 * r + 0.587 * g + 0.114 * b);

                gray = Math.min(255, gray);

                int newPixel = (gray << 16) | (gray << 8) | gray;
                grayImage.setRGB(x, y, newPixel);
            }
        }

        return grayImage;
    }
}
