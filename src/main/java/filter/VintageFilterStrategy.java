package filter;

import java.awt.image.BufferedImage;

public class VintageFilterStrategy implements FilterStrategy {

    @Override
    public String getFilterName() {
        return "Vintage";
    }

    @Override
    public BufferedImage applyFilter(BufferedImage original) {
        int width = original.getWidth();
        int height = original.getHeight();

        BufferedImage vintageImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = original.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = (rgb & 0xFF);

                int tr = (int) (0.393 * r + 0.769 * g + 0.189 * b);
                int tg = (int) (0.349 * r + 0.686 * g + 0.168 * b);
                int tb = (int) (0.272 * r + 0.534 * g + 0.131 * b);

                r = Math.min(255, tr);
                g = Math.min(255, tg);
                b = Math.min(255, tb);

                int newPixel = (r << 16) | (g << 8) | b;
                vintageImage.setRGB(x, y, newPixel);
            }
        }

        return vintageImage;
    }
}