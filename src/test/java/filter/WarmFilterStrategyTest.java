package filter;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

class WarmFilterStrategyTest {

    private static BufferedImage solid(int rgb, int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) img.setRGB(x, y, rgb);
        return img;
    }

    @Test
    void warmsNeutralGray() {
        BufferedImage out = new WarmFilterStrategy().applyFilter(solid(0x808080, 4, 3));
        int rgb = out.getRGB(2, 1);
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        assertEquals(128 + WarmFilterStrategy.RED_BOOST, r);
        assertEquals(128 + WarmFilterStrategy.GREEN_BOOST, g);
        assertEquals(128 - WarmFilterStrategy.BLUE_CUT, b);
        assertTrue(r > g && g > b);
    }

    @Test
    void clampsAtChannelLimits() {
        int white = new WarmFilterStrategy().applyFilter(solid(0xFFFFFF, 1, 1)).getRGB(0, 0) & 0xFFFFFF;
        assertEquals(0xFFFF00 | (255 - WarmFilterStrategy.BLUE_CUT), white);
        int black = new WarmFilterStrategy().applyFilter(solid(0x000000, 1, 1)).getRGB(0, 0) & 0xFFFFFF;
        assertEquals((WarmFilterStrategy.RED_BOOST << 16) | (WarmFilterStrategy.GREEN_BOOST << 8), black);
    }

    @Test
    void keepsSizeAndDoesNotMutateOriginal() {
        BufferedImage src = solid(0x336699, 7, 5);
        BufferedImage out = new WarmFilterStrategy().applyFilter(src);
        assertNotSame(src, out);
        assertEquals(7, out.getWidth());
        assertEquals(5, out.getHeight());
        assertEquals(0x336699, src.getRGB(3, 3) & 0xFFFFFF);
        assertEquals("Warm", new WarmFilterStrategy().getFilterName());
    }
}
