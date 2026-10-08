package template;

import model.StripTemplate;
import template.StripLayout.Rect;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.TexturePaint;
import java.awt.font.FontRenderContext;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.text.AttributedString;
import java.util.ArrayList;
import java.util.List;

/**
 * Strip bermerek Van de Booth: kertas putih, foto dengan sudut membulat, footer wordmark
 * "Van de Booth" ("de" italic, "oo" vermilion) dan caption acara. Ukuran canvas, posisi sel,
 * dan area footer datang dari {@link StripLayout}; frame berapa pun rasionya di-crop ke rasio sel.
 */
public class BrandedStripTemplate implements StripTemplate {

    static final int CELL_RADIUS = 14;
    /** Bagian atas foto dipertahankan lebih banyak (kepala): sisa crop vertikal dibuang 25% di atas, 75% di bawah. */
    static final double TOP_BIAS = 0.25;
    /** Batas foto yang dibuang per sisi (fraksi dimensi sumber). */
    public static final double MAX_CROP_PER_SIDE = 0.125;

    static final Color PAPER = Color.WHITE;
    static final Color INK = new Color(0x241B16);
    static final Color INK_2 = new Color(0x6B5D52);
    static final Color VERMILION = new Color(0xD9411E);
    static final Color TINT = new Color(0xEFE2CC);

    private static final String WORDMARK = "Van de Booth";
    private static final double LINE = 1.2;

    private final StripLayout layout;
    private final String caption;

    public BrandedStripTemplate(StripLayout layout, String caption) {
        if (layout == null) throw new IllegalArgumentException("layout tidak boleh null");
        this.layout = layout;
        this.caption = caption == null ? "" : caption.trim();
    }

    public StripLayout getLayout() {
        return layout;
    }

    public String getCaption() {
        return caption;
    }

    @Override
    public String getTemplateName() {
        return layout.displayName();
    }

    @Override
    public String getTemplateId() {
        return layout.id();
    }

    @Override
    public int getPhotoCount() {
        return layout.photos();
    }

    @Override
    public int getMaxPhotos() {
        return layout.photos();
    }

    public int width() {
        return layout.canvasWidth();
    }

    public int height() {
        return layout.canvasHeight();
    }

    /**
     * Area sumber (x, y, lebar, tinggi) yang di-crop agar berrasio sama dengan sel cw x ch.
     * Horizontal di tengah; vertikal dengan bias ke atas supaya kepala tidak terpotong.
     */
    public static Rectangle cropRect(int sw, int sh, int cw, int ch) {
        double target = (double) cw / ch;
        int w = sw;
        int h = (int) Math.round(sw / target);
        if (h > sh) {
            h = sh;
            w = (int) Math.round(sh * target);
        }
        return new Rectangle((sw - w) / 2, (int) Math.round((sh - h) * TOP_BIAS), w, h);
    }

    /** Crop (lihat {@link #cropRect}) lalu scale ke ukuran sel; filter berjalan pada gambar sebesar ini. */
    public static BufferedImage fitCell(BufferedImage src, Rect cell) {
        if (src.getWidth() == cell.w() && src.getHeight() == cell.h()) return src;
        Rectangle r = cropRect(src.getWidth(), src.getHeight(), cell.w(), cell.h());
        BufferedImage out = new BufferedImage(cell.w(), cell.h(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(src, 0, 0, cell.w(), cell.h(), r.x, r.y, r.x + r.width, r.y + r.height, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    @Override
    public BufferedImage applyTemplate(ArrayList<BufferedImage> images) {
        List<BufferedImage> frames = images == null ? List.of() : images;
        BufferedImage strip = new BufferedImage(width(), height(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = strip.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setColor(PAPER);
            g.fillRect(0, 0, width(), height());

            List<Rect> cells = layout.cells();
            for (int i = 0; i < cells.size(); i++) {
                Rect c = cells.get(i);
                RoundRectangle2D shape = new RoundRectangle2D.Float(c.x(), c.y(), c.w(), c.h(),
                        2 * CELL_RADIUS, 2 * CELL_RADIUS);
                BufferedImage frame = i < frames.size() ? frames.get(i) : null;
                if (frame == null) {
                    g.setPaint(TINT);
                } else {
                    g.setPaint(new TexturePaint(fitCell(frame, c), new Rectangle(c.x(), c.y(), c.w(), c.h())));
                }
                g.fill(shape);
            }
            drawFooter(g);
        } finally {
            g.dispose();
        }
        return strip;
    }

    // ------------------------------------------------------------------ footer

    private void drawFooter(Graphics2D g) {
        FontRenderContext frc = g.getFontRenderContext();
        Rect f = layout.footer();
        int markSize = layout.wordmarkSize();
        int capSize = layout.captionSize();
        switch (layout.footerStyle()) {
            case CENTER -> drawCenter(g, frc, f, markSize, capSize);
            case SPLIT -> drawSplit(g, frc, f, markSize, capSize);
            case SIDE -> drawSide(g, frc, f, markSize, capSize);
        }
    }

    private void drawCenter(Graphics2D g, FontRenderContext frc, Rect f, int markSize, int capSize) {
        double maxW = f.w() * 0.84;
        TextLayout mark = wordmark(WORDMARK, markSize, maxW, frc);
        boolean hasCaption = !caption.isEmpty();
        TextLayout cap = hasCaption ? captionLayout(caption, capSize, maxW, frc) : null;
        double markLine = markSize * LINE;
        double capLine = hasCaption ? capSize * LINE : 0;
        double gap = hasCaption ? capSize * 0.3 : 0;
        double top = f.y() + (f.h() - (markLine + gap + capLine)) / 2.0;
        drawLine(g, mark, f.x() + (f.w() - mark.getAdvance()) / 2.0, top, markLine, INK);
        if (cap != null) {
            drawLine(g, cap, f.x() + (f.w() - cap.getAdvance()) / 2.0, top + markLine + gap, capLine, INK_2);
        }
    }

    private void drawSplit(Graphics2D g, FontRenderContext frc, Rect f, int markSize, int capSize) {
        int left = layout.cells().get(0).x();
        int right = layout.cells().stream().mapToInt(Rect::right).max().orElse(width());
        double midY = f.y() + f.h() / 2.0;
        TextLayout mark = wordmark(WORDMARK, markSize, (right - left) * 0.5, frc);
        drawLine(g, mark, left, midY - markSize * LINE / 2.0, markSize * LINE, INK);
        if (!caption.isEmpty()) {
            double room = (right - left) - mark.getAdvance() - 40;
            TextLayout cap = captionLayout(caption, capSize, Math.max(120, room), frc);
            drawLine(g, cap, right - cap.getAdvance(), midY - capSize * LINE / 2.0, capSize * LINE, INK_2);
        }
    }

    private void drawSide(Graphics2D g, FontRenderContext frc, Rect f, int markSize, int capSize) {
        String[] words = WORDMARK.split(" ");
        List<String> capLines = new ArrayList<>();
        if (!caption.isEmpty()) {
            int dot = caption.lastIndexOf(" · ");
            if (dot > 0) {
                capLines.add(caption.substring(0, dot));
                capLines.add(caption.substring(dot + 3));
            } else {
                capLines.add(caption);
            }
        }
        double markLine = markSize * LINE * 0.92;
        double capLine = capSize * LINE;
        double gap = capLines.isEmpty() ? 0 : capSize * 0.9;
        double total = words.length * markLine + gap + capLines.size() * capLine;
        double top = f.y() + (f.h() - total) / 2.0;
        double maxW = f.w() * 0.8;
        for (String word : words) {
            TextLayout t = wordmark(word, markSize, maxW, frc);
            drawLine(g, t, f.x() + (f.w() - t.getAdvance()) / 2.0, top, markLine, INK);
            top += markLine;
        }
        top += gap;
        for (String line : capLines) {
            TextLayout t = captionLayout(line, capSize, maxW, frc);
            drawLine(g, t, f.x() + (f.w() - t.getAdvance()) / 2.0, top, capLine, INK_2);
            top += capLine;
        }
    }

    /** Menggambar teks pada kotak baris setinggi {@code line} yang dimulai di y = top. */
    private static void drawLine(Graphics2D g, TextLayout t, double x, double top, double line, Color color) {
        double baseline = top + (line + t.getAscent() - t.getDescent()) / 2.0;
        g.setColor(color);
        t.draw(g, (float) x, (float) baseline);
    }

    private static TextLayout wordmark(String text, int size, double maxWidth, FontRenderContext frc) {
        TextLayout t = wordmarkAt(text, size, frc);
        if (t.getAdvance() > maxWidth) {
            t = wordmarkAt(text, Math.max(8, (int) Math.floor(size * maxWidth / t.getAdvance())), frc);
        }
        return t;
    }

    private static TextLayout wordmarkAt(String text, int size, FontRenderContext frc) {
        AttributedString mark = new AttributedString(text);
        mark.addAttribute(TextAttribute.FONT, BrandFonts.serif(size));
        mark.addAttribute(TextAttribute.FOREGROUND, INK);
        mark.addAttribute(TextAttribute.TRACKING, -0.02f);
        int de = text.indexOf("de");
        if (de >= 0) mark.addAttribute(TextAttribute.FONT, BrandFonts.serifItalic(size), de, de + 2);
        int oo = text.indexOf("oo");
        if (oo >= 0) mark.addAttribute(TextAttribute.FOREGROUND, VERMILION, oo, oo + 2);
        return new TextLayout(mark.getIterator(), frc);
    }

    private static TextLayout captionLayout(String text, int size, double maxWidth, FontRenderContext frc) {
        TextLayout t = new TextLayout(text, BrandFonts.sans(size), frc);
        if (t.getAdvance() > maxWidth) {
            t = new TextLayout(text, BrandFonts.sans(Math.max(8, (int) Math.floor(size * maxWidth / t.getAdvance()))), frc);
        }
        return t;
    }

    @Override
    public BufferedImage getPreviewImage() {
        BufferedImage full = applyTemplate(new ArrayList<>());
        int w = full.getWidth() / 5;
        int h = full.getHeight() / 5;
        BufferedImage small = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = small.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(full, 0, 0, w, h, null);
        } finally {
            g.dispose();
        }
        return small;
    }
}
