package filter;

import java.awt.image.BufferedImage;

public class NoFilterStrategy implements FilterStrategy {

    @Override
    public String getFilterName() { return "Normal"; }

    @Override
    public BufferedImage applyFilter(BufferedImage original) {
        return original;
    }
}