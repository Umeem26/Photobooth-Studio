package template;

import static org.junit.jupiter.api.Assertions.*;

import factory.TemplateFactory;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import template.StripLayout.Rect;

class BrandedStripTemplateTest {

    private static BufferedImage solid(int w, int h, Color c) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(c);
        g.fillRect(0, 0, w, h);
        g.dispose();
        return img;
    }

    private static boolean hasColorNear(BufferedImage img, Rect r, Color c, int tol) {
        for (int y = r.y(); y < r.bottom(); y++) {
            for (int x = r.x(); x < r.right(); x++) {
                Color p = new Color(img.getRGB(x, y));
                if (Math.abs(p.getRed() - c.getRed()) <= tol && Math.abs(p.getGreen() - c.getGreen()) <= tol
                        && Math.abs(p.getBlue() - c.getBlue()) <= tol) return true;
            }
        }
        return false;
    }

    private static Rectangle rect(Rect r) {
        return new Rectangle(r.x(), r.y(), r.w(), r.h());
    }

    private static final String CAPTION = "Sample event · 12.10.2026";

    @Test
    void canvasSizesAndPhotoCountsMatchSpec() {
        record Spec(StripLayout l, int w, int h, int photos, String paper) { }
        List<Spec> specs = List.of(
                new Spec(StripLayout.VERTICAL_4, 600, 1800, 4, "2x6"),
                new Spec(StripLayout.VERTICAL_3, 600, 1800, 3, "2x6"),
                new Spec(StripLayout.HORIZONTAL_3, 1800, 1200, 3, "6x4"),
                new Spec(StripLayout.POSTCARD_1, 1800, 1200, 1, "6x4"),
                new Spec(StripLayout.GRID_4, 1800, 1200, 4, "6x4"),
                new Spec(StripLayout.GRID_6, 1200, 1800, 6, "4x6"));
        assertEquals(StripLayout.values().length, specs.size());
        for (Spec s : specs) {
            assertEquals(s.w, s.l.canvasWidth(), s.l.id());
            assertEquals(s.h, s.l.canvasHeight(), s.l.id());
            assertEquals(s.photos, s.l.photos(), s.l.id());
            assertEquals(s.paper, s.l.paper(), s.l.id());
        }
        assertEquals(new Rect(72, 72, 1080, 810), StripLayout.HORIZONTAL_3.cells().get(0));
        assertEquals(new Rect(1176, 72, 528, 396), StripLayout.HORIZONTAL_3.cells().get(1));
        assertEquals(new Rect(180, 60, 1440, 960), StripLayout.POSTCARD_1.cells().get(0));
        assertEquals(StripLayout.VERTICAL_4, StripLayout.byId("vertical-4").orElseThrow());
        assertTrue(StripLayout.byId("wide-9").isEmpty());
        assertTrue(StripLayout.VERTICAL_3.twoUpAllowed());
        assertFalse(StripLayout.GRID_6.twoUpAllowed());
    }

    @ParameterizedTest
    @EnumSource(StripLayout.class)
    void cellsAreInsideCanvasWithoutOverlapAndFooterIsFree(StripLayout l) {
        Rectangle canvas = new Rectangle(0, 0, l.canvasWidth(), l.canvasHeight());
        List<Rect> cells = l.cells();
        for (Rect c : cells) {
            assertTrue(canvas.contains(rect(c)), l.id() + " sel di dalam canvas");
        }
        for (int i = 0; i < cells.size(); i++) {
            for (int j = i + 1; j < cells.size(); j++) {
                assertFalse(rect(cells.get(i)).intersects(rect(cells.get(j))),
                        l.id() + " sel " + i + " dan " + j + " tumpang tindih");
            }
        }
        Rect f = l.footer();
        assertTrue(f.w() > 0 && f.h() > 0 && canvas.contains(rect(f)), l.id() + " footer ada");
        for (Rect c : cells) {
            assertFalse(rect(f).intersects(rect(c)), l.id() + " footer bebas dari sel");
        }
    }

    @ParameterizedTest
    @EnumSource(StripLayout.class)
    void cropDiscardsAtMostOneEighthPerSideForFourByThreePhotos(StripLayout l) {
        for (Rect c : l.cells()) {
            Rectangle r = BrandedStripTemplate.cropRect(2560, 1920, c.w(), c.h());
            assertEquals((double) c.w() / c.h(), (double) r.width / r.height, 0.01, l.id() + " rasio crop = rasio sel");
            double left = (double) r.x / 2560;
            double right = (double) (2560 - r.x - r.width) / 2560;
            double top = (double) r.y / 1920;
            double bottom = (double) (1920 - r.y - r.height) / 1920;
            for (double d : new double[] {left, right, top, bottom}) {
                assertTrue(d <= BrandedStripTemplate.MAX_CROP_PER_SIDE + 1e-9, l.id() + " crop " + d);
            }
            assertTrue(top <= bottom + 1e-9, l.id() + " bias ke atas: bagian atas dibuang tidak lebih banyak");
        }
    }

    @ParameterizedTest
    @EnumSource(StripLayout.class)
    void composesEachLayoutWithBrandedFooterUnderBudget(StripLayout l) {
        BrandedStripTemplate t = new BrandedStripTemplate(l, CAPTION);
        ArrayList<BufferedImage> frames = new ArrayList<>();
        for (int i = 0; i < l.photos(); i++) {
            frames.add(BrandedStripTemplate.fitCell(solid(1920, 1440, new Color(40 * (i + 1), 90, 200)), l.cells().get(i)));
        }
        t.applyTemplate(frames); // pemanasan font dan JIT
        long start = System.nanoTime();
        BufferedImage strip = t.applyTemplate(frames);
        long ms = (System.nanoTime() - start) / 1_000_000;

        assertEquals(l.canvasWidth(), strip.getWidth());
        assertEquals(l.canvasHeight(), strip.getHeight());
        assertTrue(ms < 1500, l.id() + " compose " + ms + " ms");
        for (int i = 0; i < l.photos(); i++) {
            Rect c = l.cells().get(i);
            assertEquals(new Color(40 * (i + 1), 90, 200).getRGB(),
                    strip.getRGB(c.x() + c.w() / 2, c.y() + c.h() / 2), l.id() + " sel " + i);
        }
        assertEquals(Color.WHITE.getRGB(), strip.getRGB(2, 2), "tepi kertas putih");
        assertTrue(hasColorNear(strip, l.footer(), BrandedStripTemplate.INK, 30), l.id() + " wordmark ink");
        assertTrue(hasColorNear(strip, l.footer(), BrandedStripTemplate.VERMILION, 30), l.id() + " oo vermilion");
        assertTrue(hasColorNear(strip, l.footer(), BrandedStripTemplate.INK_2, 30), l.id() + " caption ink-2");
    }

    @Test
    void missingFramesLeaveTintPlaceholderAndBlankCaptionIsEmpty() {
        BrandedStripTemplate t = new BrandedStripTemplate(StripLayout.HORIZONTAL_3, "  ");
        BufferedImage strip = t.applyTemplate(new ArrayList<>(List.of(solid(1440, 1080, Color.CYAN))));
        Rect hero = StripLayout.HORIZONTAL_3.cells().get(0);
        Rect small = StripLayout.HORIZONTAL_3.cells().get(1);
        assertEquals(Color.CYAN.getRGB(), strip.getRGB(hero.x() + 100, hero.y() + 100));
        assertEquals(BrandedStripTemplate.TINT.getRGB(), strip.getRGB(small.x() + 100, small.y() + 100));
        assertEquals("", t.getCaption());
    }

    @Test
    void fitCellCropsCenterHorizontallyAndKeepsTopVertically() {
        Rect cell = StripLayout.VERTICAL_3.cells().get(0); // 504x432: kiri-kanan terpotong
        BufferedImage src = solid(1600, 1200, Color.BLACK);
        Graphics2D g = src.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 100, 1200);
        g.dispose();
        BufferedImage fitted = BrandedStripTemplate.fitCell(src, cell);
        assertEquals(cell.w(), fitted.getWidth());
        assertEquals(cell.h(), fitted.getHeight());
        assertEquals(Color.BLACK.getRGB(), fitted.getRGB(2, 200), "pita kiri terpotong");

        Rect wide = StripLayout.VERTICAL_4.cells().get(0); // 504x360: atas-bawah terpotong, bias ke atas
        Rectangle r = BrandedStripTemplate.cropRect(1600, 1200, wide.w(), wide.h());
        assertTrue(r.y >= 0 && r.y < (1200 - r.height) / 2, "crop vertikal condong ke atas");
    }

    @Test
    void factoryCreatesBrandedTemplatesAndKeepsLegacyIds() {
        TemplateFactory f = new TemplateFactory();
        for (StripLayout l : StripLayout.values()) {
            assertInstanceOf(BrandedStripTemplate.class, f.createTemplate(l.id(), "x"));
            assertEquals(l.photos(), f.createTemplate(l.id(), "x").getPhotoCount());
        }
        assertInstanceOf(TemplateVertical.class, f.createTemplate("TPL-V-4", "x"));
        assertNull(f.createTemplate("square-9", "x"));
    }

    @Test
    void brandFontsAreBundled() {
        assertTrue(BrandFonts.brandFontsLoaded(), "Fraunces/Plus Jakarta Sans harus termuat dari resources");
    }
}
