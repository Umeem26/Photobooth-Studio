package template;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

import factory.TemplateFactory;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

class BrandedStripTemplateTest {

    private static BufferedImage solid(int w, int h, Color c) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(c);
        g.fillRect(0, 0, w, h);
        g.dispose();
        return img;
    }

    private static boolean hasColorNear(BufferedImage img, int x0, int y0, int x1, int y1, Color c, int tol) {
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                Color p = new Color(img.getRGB(x, y));
                if (Math.abs(p.getRed() - c.getRed()) <= tol && Math.abs(p.getGreen() - c.getGreen()) <= tol
                        && Math.abs(p.getBlue() - c.getBlue()) <= tol) return true;
            }
        }
        return false;
    }

    @Test
    void verticalFourHasExpectedSizeAndCells() {
        BrandedStripTemplate t = new BrandedStripTemplate(StripLayout.VERTICAL_4, "Sample event · 12.10.2026");
        Color[] colors = {Color.RED, Color.GREEN, Color.BLUE, Color.ORANGE};
        ArrayList<BufferedImage> frames = new ArrayList<>();
        for (Color c : colors) frames.add(solid(1920, 1080, c)); // 16:9 -> crop 4:3

        BufferedImage strip = t.applyTemplate(frames);

        assertEquals(t.width(), strip.getWidth());
        assertEquals(t.height(), strip.getHeight());
        assertEquals(2 * BrandedStripTemplate.PAD + BrandedStripTemplate.CELL_W, strip.getWidth());
        for (int i = 0; i < 4; i++) {
            int cx = BrandedStripTemplate.PAD + BrandedStripTemplate.CELL_W / 2;
            int cy = BrandedStripTemplate.PAD + i * (BrandedStripTemplate.CELL_H + BrandedStripTemplate.GAP)
                    + BrandedStripTemplate.CELL_H / 2;
            assertEquals(colors[i].getRGB(), strip.getRGB(cx, cy), "sel " + i);
        }
        assertEquals(Color.WHITE.getRGB(), strip.getRGB(5, 5), "tepi kertas putih");
    }

    @Test
    void footerDrawsInkWordmarkWithVermilionOoAndCaption() {
        BrandedStripTemplate t = new BrandedStripTemplate(StripLayout.VERTICAL_3, "Sample event · 12.10.2026");
        BufferedImage strip = t.applyTemplate(new ArrayList<>(List.of(
                solid(800, 600, Color.GRAY), solid(800, 600, Color.GRAY), solid(800, 600, Color.GRAY))));
        int footerTop = strip.getHeight() - BrandedStripTemplate.PAD_BOTTOM - t.footerHeight();
        int w = strip.getWidth();
        int h = strip.getHeight();

        assertTrue(hasColorNear(strip, 0, footerTop, w, h, BrandedStripTemplate.INK, 30), "wordmark ink");
        assertTrue(hasColorNear(strip, 0, footerTop, w, h, BrandedStripTemplate.VERMILION, 30), "oo vermilion");
        int captionTop = footerTop + BrandedStripTemplate.FOOTER_TOP + BrandedStripTemplate.WORDMARK_LINE;
        assertTrue(hasColorNear(strip, 0, captionTop, w, h, BrandedStripTemplate.INK_2, 30), "caption ink-2");
    }

    @Test
    void horizontalThreeLaysOutInRowAndEmptyCaptionShrinksFooter() {
        BrandedStripTemplate withCaption = new BrandedStripTemplate(StripLayout.HORIZONTAL_3, "Event");
        BrandedStripTemplate noCaption = new BrandedStripTemplate(StripLayout.HORIZONTAL_3, "  ");
        BufferedImage strip = noCaption.applyTemplate(new ArrayList<>(List.of(solid(640, 480, Color.CYAN))));

        assertEquals(2 * BrandedStripTemplate.PAD + 3 * BrandedStripTemplate.CELL_W + 2 * BrandedStripTemplate.GAP,
                strip.getWidth());
        assertTrue(noCaption.height() < withCaption.height());
        // sel ke-2 dan ke-3 belum ada frame -> placeholder tint
        int cy = BrandedStripTemplate.PAD + BrandedStripTemplate.CELL_H / 2;
        int cx2 = BrandedStripTemplate.PAD + BrandedStripTemplate.CELL_W + BrandedStripTemplate.GAP + 100;
        assertEquals(BrandedStripTemplate.TINT.getRGB(), strip.getRGB(cx2, cy));
        assertEquals(Color.CYAN.getRGB(), strip.getRGB(BrandedStripTemplate.PAD + 100, cy));
    }

    @Test
    void fitCellCropsCenterToFourByThree() {
        BufferedImage src = solid(1600, 900, Color.BLACK);
        Graphics2D g = src.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 200, 900);   // pita kiri terpotong oleh crop tengah
        g.dispose();
        BufferedImage cell = BrandedStripTemplate.fitCell(src);
        assertEquals(BrandedStripTemplate.CELL_W, cell.getWidth());
        assertEquals(BrandedStripTemplate.CELL_H, cell.getHeight());
        assertEquals(Color.BLACK.getRGB(), cell.getRGB(2, 240));
    }

    @Test
    void factoryCreatesBrandedTemplatesAndKeepsLegacyIds() {
        TemplateFactory f = new TemplateFactory();
        assertInstanceOf(BrandedStripTemplate.class, f.createTemplate("vertical-4", "x"));
        assertEquals(3, f.createTemplate("horizontal-3", "x").getPhotoCount());
        assertInstanceOf(TemplateVertical.class, f.createTemplate("TPL-V-4", "x"));
        assertNull(f.createTemplate("square-9", "x"));
    }

    @Test
    void brandFontsAreBundled() {
        assertTrue(BrandFonts.brandFontsLoaded(), "Fraunces/Plus Jakarta Sans harus termuat dari resources");
    }
}
