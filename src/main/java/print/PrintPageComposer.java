package print;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Menyusun halaman kertas 4x6 inci (300 dpi) dari strip: skala ke area cetak dengan
 * margin 3 mm tanpa mengubah rasio. two-up = dua strip berdampingan untuk dipotong.
 * Strip vertikal memakai halaman potret, strip horizontal halaman lanskap.
 */
public final class PrintPageComposer {

    public static final int DPI = 300;
    public static final int SHORT = 4 * DPI;   // 1200 px
    public static final int LONG = 6 * DPI;    // 1800 px
    public static final int MARGIN = Math.round(3 / 25.4f * DPI); // 3 mm = 35 px

    private PrintPageComposer() {
    }

    public static BufferedImage compose(BufferedImage strip, boolean twoUp) {
        boolean portrait = strip.getHeight() >= strip.getWidth();
        int w = portrait ? SHORT : LONG;
        int h = portrait ? LONG : SHORT;
        BufferedImage page = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = page.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, w, h);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            if (!twoUp) {
                draw(g, strip, new Rectangle(MARGIN, MARGIN, w - 2 * MARGIN, h - 2 * MARGIN));
            } else if (portrait) {
                int half = w / 2;
                draw(g, strip, new Rectangle(MARGIN, MARGIN, half - 2 * MARGIN, h - 2 * MARGIN));
                draw(g, strip, new Rectangle(half + MARGIN, MARGIN, half - 2 * MARGIN, h - 2 * MARGIN));
            } else {
                int half = h / 2;
                draw(g, strip, new Rectangle(MARGIN, MARGIN, w - 2 * MARGIN, half - 2 * MARGIN));
                draw(g, strip, new Rectangle(MARGIN, half + MARGIN, w - 2 * MARGIN, half - 2 * MARGIN));
            }
        } finally {
            g.dispose();
        }
        return page;
    }

    /** Area tujuan (x, y, lebar, tinggi) bila gambar sw x sh dimuat ke box dengan rasio tetap, di tengah. */
    public static Rectangle fit(int sw, int sh, Rectangle box) {
        double scale = Math.min((double) box.width / sw, (double) box.height / sh);
        int dw = (int) Math.round(sw * scale);
        int dh = (int) Math.round(sh * scale);
        return new Rectangle(box.x + (box.width - dw) / 2, box.y + (box.height - dh) / 2, dw, dh);
    }

    private static void draw(Graphics2D g, BufferedImage img, Rectangle box) {
        Rectangle r = fit(img.getWidth(), img.getHeight(), box);
        g.drawImage(img, r.x, r.y, r.width, r.height, null);
    }
}
