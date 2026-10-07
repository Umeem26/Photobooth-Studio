package service;

import filter.FilterStrategy;
import filter.GrayscaleFilterStrategy;
import filter.NoFilterStrategy;
import filter.VintageFilterStrategy;
import filter.WarmFilterStrategy;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Filter yang ditawarkan UI baru, dipetakan ke FilterStrategy yang ada.
 */
public enum BoothFilter {
    ORIGINAL("original", "Original", NoFilterStrategy::new),
    MONO("mono", "Black & white", GrayscaleFilterStrategy::new),
    VINTAGE("vintage", "Vintage", VintageFilterStrategy::new),
    WARM("warm", "Warm", WarmFilterStrategy::new);

    private final String id;
    private final String displayName;
    private final Supplier<FilterStrategy> strategy;

    BoothFilter(String id, String displayName, Supplier<FilterStrategy> strategy) {
        this.id = id;
        this.displayName = displayName;
        this.strategy = strategy;
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public FilterStrategy strategy() { return strategy.get(); }

    public static Optional<BoothFilter> byId(String id) {
        return Arrays.stream(values()).filter(f -> f.id.equals(id)).findFirst();
    }
}
