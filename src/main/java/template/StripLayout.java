package template;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Enam layout yang ditawarkan UI, didefinisikan sebagai data di satu tempat: ukuran canvas
 * (px @300 DPI), sel foto, dan area footer. Dipakai compose, API /api/layouts, dan cetak.
 */
public enum StripLayout {
    VERTICAL_4("vertical-4", "Classic Strip", "The classic booth look.", "2x6", 600, 1800,
            FooterStyle.CENTER, new Rect(0, 1560, 600, 240), 64, 30, column(48, 48, 504, 360, 24, 4)),
    VERTICAL_3("vertical-3", "Tall Strip", "Roomier, with a bigger signature.", "2x6", 600, 1800,
            FooterStyle.CENTER, new Rect(0, 1400, 600, 400), 84, 34, column(48, 48, 504, 432, 28, 3)),
    HORIZONTAL_3("horizontal-3", "Wide", "One hero shot and two close-ups.", "6x4", 1800, 1200,
            FooterStyle.SPLIT, new Rect(0, 882, 1800, 318), 96, 40,
            List.of(new Rect(72, 72, 1080, 810), new Rect(1176, 72, 528, 396), new Rect(1176, 486, 528, 396))),
    POSTCARD_1("postcard-1", "Big Shot", "One big photo, for groups and outfits.", "6x4", 1800, 1200,
            FooterStyle.SPLIT, new Rect(0, 1020, 1800, 180), 72, 34, List.of(new Rect(180, 60, 1440, 960))),
    GRID_4("grid-4", "Four Square", "Four poses in a tidy grid.", "6x4", 1800, 1200,
            FooterStyle.SIDE, new Rect(1404, 0, 396, 1200), 96, 30,
            grid(60, 60, 660, 496, 24, 2, 2)),
    GRID_6("grid-6", "Contact Sheet", "Six quick shots on one sheet.", "4x6", 1200, 1800,
            FooterStyle.CENTER, new Rect(0, 1384, 1200, 416), 110, 44, grid(84, 64, 504, 424, 24, 2, 3));

    /** Persegi panjang dalam px canvas. */
    public record Rect(int x, int y, int w, int h) {
        public int right() { return x + w; }
        public int bottom() { return y + h; }
    }

    /** Gaya footer: CENTER = wordmark dan caption di tengah; SPLIT = wordmark kiri, caption kanan; SIDE = kolom kanan. */
    public enum FooterStyle { CENTER, SPLIT, SIDE }

    public enum Orientation { VERTICAL, HORIZONTAL }

    private final String id;
    private final String displayName;
    private final String description;
    private final String paper;
    private final int canvasWidth;
    private final int canvasHeight;
    private final FooterStyle footerStyle;
    private final Rect footer;
    private final int wordmarkSize;
    private final int captionSize;
    private final List<Rect> cells;

    StripLayout(String id, String displayName, String description, String paper, int canvasWidth, int canvasHeight,
                FooterStyle footerStyle, Rect footer, int wordmarkSize, int captionSize, List<Rect> cells) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.paper = paper;
        this.canvasWidth = canvasWidth;
        this.canvasHeight = canvasHeight;
        this.footerStyle = footerStyle;
        this.footer = footer;
        this.wordmarkSize = wordmarkSize;
        this.captionSize = captionSize;
        this.cells = List.copyOf(cells);
    }

    private static List<Rect> column(int x, int y, int w, int h, int gap, int n) {
        return grid(x, y, w, h, gap, 1, n);
    }

    private static List<Rect> grid(int x, int y, int w, int h, int gap, int cols, int rows) {
        Rect[] out = new Rect[cols * rows];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                out[r * cols + c] = new Rect(x + c * (w + gap), y + r * (h + gap), w, h);
            }
        }
        return List.of(out);
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public String description() { return description; }
    /** Ukuran kertas cetak asal layout: "2x6", "6x4", atau "4x6". */
    public String paper() { return paper; }
    public int canvasWidth() { return canvasWidth; }
    public int canvasHeight() { return canvasHeight; }
    public FooterStyle footerStyle() { return footerStyle; }
    public Rect footer() { return footer; }
    public int wordmarkSize() { return wordmarkSize; }
    public int captionSize() { return captionSize; }
    public List<Rect> cells() { return cells; }
    public int photos() { return cells.size(); }

    public Orientation orientation() {
        return canvasWidth > canvasHeight ? Orientation.HORIZONTAL : Orientation.VERTICAL;
    }

    /** Strip 2x6 yang boleh dicetak two-up (dua per kertas 4x6). */
    public boolean twoUpAllowed() {
        return "2x6".equals(paper);
    }

    public static Optional<StripLayout> byId(String id) {
        return Arrays.stream(values()).filter(l -> l.id.equals(id)).findFirst();
    }
}
