package share;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Rate limit sederhana per kunci (IP): maksimal {@code limit} request per jendela 60 detik. */
public final class RateLimiter {

    private static final long WINDOW_MS = 60_000;

    private record Window(long start, int count) { }

    private final int limit;
    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimiter(int limit, Clock clock) {
        if (limit < 1) throw new IllegalArgumentException("limit minimal 1");
        this.limit = limit;
        this.clock = clock;
    }

    public boolean allow(String key) {
        long now = clock.millis();
        Window w = windows.compute(key, (k, old) ->
                old == null || now - old.start() >= WINDOW_MS ? new Window(now, 1) : new Window(old.start(), old.count() + 1));
        if (windows.size() > 10_000) {
            windows.values().removeIf(x -> now - x.start() >= WINDOW_MS);
        }
        return w.count() <= limit;
    }
}
