package share;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Token berbagi per sesi: 16 byte acak base64url (22 karakter), kedaluwarsa sesuai
 * share.expiryHours. Disimpan di memori; token kedaluwarsa dianggap tidak ada.
 */
public final class ShareRegistry {

    /** sessionId null = halaman uji (Mode Operator). */
    public record Entry(String token, String sessionId, Instant expiresAt) {
        public boolean isTest() {
            return sessionId == null;
        }
    }

    public static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_-]{22}");

    private final SecureRandom random = new SecureRandom();
    private final Map<String, Entry> byToken = new ConcurrentHashMap<>();
    private final Clock clock;

    public ShareRegistry(Clock clock) {
        this.clock = clock;
    }

    /** Memakai ulang token sesi yang masih berlaku, atau membuat yang baru. */
    public synchronized Entry linkFor(String sessionId, Duration ttl) {
        Instant now = clock.instant();
        if (sessionId != null) {
            for (Entry e : byToken.values()) {
                if (sessionId.equals(e.sessionId()) && e.expiresAt().isAfter(now)) return e;
            }
        }
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Entry entry = new Entry(token, sessionId, now.plus(ttl));
        byToken.put(token, entry);
        return entry;
    }

    /** Entri yang masih berlaku; token salah format atau kedaluwarsa -> kosong. */
    public Optional<Entry> resolve(String token) {
        if (token == null || !TOKEN.matcher(token).matches()) return Optional.empty();
        Entry e = byToken.get(token);
        if (e == null) return Optional.empty();
        if (!e.expiresAt().isAfter(clock.instant())) {
            byToken.remove(token);
            return Optional.empty();
        }
        return Optional.of(e);
    }

    /** Mencabut semua token sesi (mis. sesi dihapus di galeri). */
    public void revokeSession(String sessionId) {
        byToken.values().removeIf(e -> sessionId.equals(e.sessionId()));
    }
}
