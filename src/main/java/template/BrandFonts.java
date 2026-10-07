package template;

import java.awt.Font;
import java.awt.FontFormatException;
import java.io.IOException;
import java.io.InputStream;

/**
 * Font merek yang dibundel di resources/fonts (OFL, instans statis: Fraunces 600,
 * Fraunces Italic 400, Plus Jakarta Sans 600). Bila gagal dimuat,
 * jatuh ke font logis Java (Serif / SansSerif) agar komposisi tetap berjalan.
 */
public final class BrandFonts {

    private static final Font SERIF = load("/fonts/Fraunces-SemiBold.ttf", Font.SERIF);
    private static final Font SERIF_ITALIC = load("/fonts/Fraunces-Italic-Regular.ttf", Font.SERIF);
    private static final Font SANS = load("/fonts/PlusJakartaSans-SemiBold.ttf", Font.SANS_SERIF);

    private BrandFonts() {
    }

    private static Font load(String resource, String fallbackFamily) {
        try (InputStream in = BrandFonts.class.getResourceAsStream(resource)) {
            if (in != null) {
                return Font.createFont(Font.TRUETYPE_FONT, in);
            }
        } catch (IOException | FontFormatException e) {
            // pakai fallback di bawah
        }
        return new Font(fallbackFamily, Font.PLAIN, 1);
    }

    public static Font serif(float size) {
        return SERIF.deriveFont(size);
    }

    public static Font serifItalic(float size) {
        return SERIF_ITALIC.deriveFont(size);
    }

    public static Font sans(float size) {
        return SANS.deriveFont(size);
    }

    /** true bila semua font merek termuat dari resources (bukan fallback). */
    public static boolean brandFontsLoaded() {
        return SERIF.getFamily().startsWith("Fraunces")
                && SERIF_ITALIC.getFamily().startsWith("Fraunces")
                && SANS.getFamily().startsWith("Plus Jakarta Sans");
    }
}
