package admin;

import java.io.IOException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Login operator: PIN -> token admin (memori), kedaluwarsa 15 menit tanpa aktivitas.
 * 5 PIN salah berturut-turut mengunci login selama 30 detik.
 */
public final class AdminAuth {

    public static final int MAX_ATTEMPTS = 5;
    public static final Duration LOCKOUT = Duration.ofSeconds(30);
    public static final Duration IDLE_TIMEOUT = Duration.ofMinutes(15);

    public sealed interface LoginResult permits Success, WrongPin, Locked { }

    public record Success(String token) implements LoginResult { }

    public record WrongPin(int attemptsLeft) implements LoginResult { }

    public record Locked(long retryAfterSeconds) implements LoginResult { }

    private final PinStore pins;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Instant> tokens = new ConcurrentHashMap<>();
    private int failures;
    private Instant lockedUntil = Instant.MIN;

    public AdminAuth(PinStore pins, Clock clock) {
        this.pins = pins;
        this.clock = clock;
    }

    public boolean pinSet() {
        return pins.isSet();
    }

    /** Membuat PIN pertama lalu langsung login. */
    public synchronized String createPin(String pin) throws IOException {
        pins.create(pin);
        failures = 0;
        return issueToken();
    }

    public synchronized LoginResult login(String pin) throws IOException {
        Instant now = clock.instant();
        if (now.isBefore(lockedUntil)) {
            return new Locked(Math.max(1, Duration.between(now, lockedUntil).toSeconds() + 1));
        }
        if (pins.verify(pin)) {
            failures = 0;
            return new Success(issueToken());
        }
        failures++;
        if (failures >= MAX_ATTEMPTS) {
            failures = 0;
            lockedUntil = now.plus(LOCKOUT);
            return new Locked(LOCKOUT.toSeconds());
        }
        return new WrongPin(MAX_ATTEMPTS - failures);
    }

    private String issueToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokens.put(token, clock.instant());
        return token;
    }

    /** true bila token valid; sekaligus memperpanjang masa aktif. */
    public boolean validate(String token) {
        if (token == null) return false;
        Instant now = clock.instant();
        Instant last = tokens.get(token);
        if (last == null) return false;
        if (Duration.between(last, now).compareTo(IDLE_TIMEOUT) >= 0) {
            tokens.remove(token);
            return false;
        }
        tokens.put(token, now);
        return true;
    }

    public void logout(String token) {
        if (token != null) tokens.remove(token);
    }
}
