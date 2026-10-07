package template;

import java.util.Arrays;
import java.util.Optional;

/**
 * Layout strip yang ditawarkan UI baru (Fase 2).
 */
public enum StripLayout {
    VERTICAL_4("vertical-4", "Vertical, 4 photos", "The classic booth look.", 4, Orientation.VERTICAL),
    VERTICAL_3("vertical-3", "Vertical, 3 photos", "Roomier, great for one hero pose.", 3, Orientation.VERTICAL),
    HORIZONTAL_3("horizontal-3", "Horizontal, 3 photos", "Wide, made for framing or display.", 3, Orientation.HORIZONTAL);

    public enum Orientation { VERTICAL, HORIZONTAL }

    private final String id;
    private final String displayName;
    private final String description;
    private final int photos;
    private final Orientation orientation;

    StripLayout(String id, String displayName, String description, int photos, Orientation orientation) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.photos = photos;
        this.orientation = orientation;
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public String description() { return description; }
    public int photos() { return photos; }
    public Orientation orientation() { return orientation; }

    public static Optional<StripLayout> byId(String id) {
        return Arrays.stream(values()).filter(l -> l.id.equals(id)).findFirst();
    }
}
