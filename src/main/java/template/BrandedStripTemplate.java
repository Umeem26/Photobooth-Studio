package template;

import model.StripTemplate;

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
 * Strip bermerek Van de Booth (Fase 2): kertas putih, foto 4:3 dengan sudut membulat,
 * footer wordmark "Van de Booth" ("de" italic, "oo" vermilion) dan caption acara.
 * Frame resolusi apa pun di-crop tengah lalu di-scale ke 4:3.
 */
public class BrandedStripTemplate implements StripTemplate {

    // Ukuran dalam px output (skala 2,5x dari strip di mockup layar hasil)
    static final int CELL_W = 640;
    static final int CELL_H = 480;
    static final int PAD = 70;
    static final int PAD_BOTTOM = 55;
    static final int GAP = 35;
    static final int CELL_RADIUS = 15;
    static final int FOOTER_TOP = 30;
    static final int WORDMARK_SIZE = 70;
    static final int WORDMARK_LINE = 84;
    static final int CAPTION_SIZE = 38;
    static final int CAPTION_GAP = 10;
    static final int CAPTION_LINE = 46;

    static final Color PAPER = Color.WHITE;
    static final Color INK = new Color(0x241B16);
    static final Color INK_2 = new Color(0x6B5D52);
    static final Color VERMILION = new Color(0xD9411E);
    static final Color TINT = new Color(0xEFE2CC);

    private static final String WORDMARK = "Van de Booth";

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

    int footerHeight() {
        return FOOTER_TOP + WORDMARK_LINE + (caption.isEmpty() ? 0 : CAPTION_GAP + CAPTION_LINE);
    }

    /** Ukuran strip output untuk layout dan caption ini. */
    public int width() {
        int n = layout.photos();
        return layout.orientation() == StripLayout.Orientation.VERTICAL
                ? 2 * PAD + CELL_W
                : 2 * PAD + n * CELL_W + (n - 1) * GAP;
    }

    public int height() {
        int n = layout.photos();
        int photos = layout.orientation() == StripLayout.Orientation.VERTICAL
                ? n * CELL_H + (n - 1) * GAP
                : CELL_H;
        return PAD + photos + footerHeight() + PAD_BOTTOM;
    }

    /**
     * Crop tengah ke 4:3 lalu scale ke ukuran sel. Dipakai juga sebelum filter
     * agar filter berjalan pada gambar kecil.
     */
    public static BufferedImage fitCell(BufferedImage src) {
        if (src.getWidth() == CELL_W && src.getHeight() == CELL_H) return src;
        double target = (double) CELL_W / CELL_H;
        int w = src.getWidth();
        int h = src.getHeight();
        int cw = w;
        int ch = (int) Math.round(w / target);
        if (ch > h) {
            ch = h;
            cw = (int) Math.round(h * target);
        }
        int x = (w - cw) / 2;
        int y = (h - ch) / 2;

        BufferedImage out = new BufferedImage(CELL_W, CELL_H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(src, 0, 0, CELL_W, CELL_H, x, y, x + cw, y + ch, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    @Override
    public BufferedImage applyTemplate(ArrayList<BufferedImage> images) {
        List<BufferedImage> frames = images == null ? List.of() : images;
        int width = width();
        int height = height();
        BufferedImage strip = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = strip.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setColor(PAPER);
            g.fillRect(0, 0, width, height);

            for (int i = 0; i < layout.photos(); i++) {
                int x = PAD;
                int y = PAD;
                if (layout.orientation() == StripLayout.Orientation.VERTICAL) {
                    y += i * (CELL_H + GAP);
                } else {
                    x += i * (CELL_W + GAP);
                }
                RoundRectangle2D cell = new RoundRectangle2D.Float(x, y, CELL_W, CELL_H, 2 * CELL_RADIUS, 2 * CELL_RADIUS);
                BufferedImage frame = i < frames.size() ? frames.get(i) : null;
                if (frame == null) {
                    g.setPaint(TINT);
                } else {
                    g.setPaint(new TexturePaint(fitCell(frame), new Rectangle(x, y, CELL_W, CELL_H)));
                }
                g.fill(cell);
            }

            int photosBottom = height - PAD_BOTTOM - footerHeight();
            drawFooter(g, width, photosBottom + FOOTER_TOP);
        } finally {
            g.dispose();
        }
        return strip;
    }

    private void drawFooter(Graphics2D g, int width, int top) {
        FontRenderContext frc = g.getFontRenderContext();

        AttributedString mark = new AttributedString(WORDMARK);
        mark.addAttribute(TextAttribute.FONT, BrandFonts.serif(WORDMARK_SIZE));
        mark.addAttribute(TextAttribute.FOREGROUND, INK);
        mark.addAttribute(TextAttribute.TRACKING, -0.02f);
        int de = WORDMARK.indexOf("de");
        mark.addAttribute(TextAttribute.FONT, BrandFonts.serifItalic(WORDMARK_SIZE), de, de + 2);
        int oo = WORDMARK.indexOf("oo");
        mark.addAttribute(TextAttribute.FOREGROUND, VERMILION, oo, oo + 2);

        TextLayout markLayout = new TextLayout(mark.getIterator(), frc);
        float markBaseline = top + (WORDMARK_LINE + markLayout.getAscent() - markLayout.getDescent()) / 2f;
        markLayout.draw(g, (width - markLayout.getAdvance()) / 2f, markBaseline);

        if (!caption.isEmpty()) {
            TextLayout captionLayout = new TextLayout(caption, BrandFonts.sans(CAPTION_SIZE), frc);
            float captionTop = top + WORDMARK_LINE + CAPTION_GAP;
            float captionBaseline = captionTop
                    + (CAPTION_LINE + captionLayout.getAscent() - captionLayout.getDescent()) / 2f;
            g.setColor(INK_2);
            captionLayout.draw(g, (width - captionLayout.getAdvance()) / 2f, captionBaseline);
        }
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
