package admin;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import server.MutableClock;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

class AdminAuthTest {

    @TempDir
    Path tmp;

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-12T10:00:00Z"));
    private final String pin = TestPins.random(6);
    private final String shortPin = TestPins.random(4);

    @Test
    void noDefaultPinAndPinStoredAsSaltedHash() throws Exception {
        PinStore pins = new PinStore(tmp);
        assertFalse(pins.isSet(), "tidak boleh ada PIN bawaan");
        assertFalse(pins.verify(TestPins.random(4)));
        assertFalse(pins.verify(TestPins.random(6)));

        assertThrows(IllegalArgumentException.class, () -> pins.create("12"));
        assertThrows(IllegalArgumentException.class, () -> pins.create("123456789"));
        assertThrows(IllegalArgumentException.class, () -> pins.create("12a4"));
        pins.create(pin);
        assertThrows(IllegalStateException.class, () -> pins.create(TestPins.other(pin)));

        String stored = Files.readString(tmp.resolve("operator-pin.properties"));
        assertFalse(stored.contains(pin), "PIN tidak boleh disimpan polos");
        assertTrue(stored.contains("PBKDF2WithHmacSHA256"));
        assertTrue(pins.verify(pin));
        assertFalse(pins.verify(TestPins.other(pin)));

        PinStore other = new PinStore(tmp.resolve("lain"));
        other.create(pin);
        assertNotEquals(stored.lines().filter(l -> l.startsWith("hash=")).findFirst(),
                Files.readString(tmp.resolve("lain").resolve("operator-pin.properties"))
                        .lines().filter(l -> l.startsWith("hash=")).findFirst(), "salt acak per instalasi");
    }

    @Test
    void lockoutAfterFiveWrongAttemptsForThirtySeconds() throws Exception {
        AdminAuth auth = new AdminAuth(new PinStore(tmp), clock);
        auth.createPin(shortPin);

        for (int left = 4; left >= 1; left--) {
            AdminAuth.LoginResult r = auth.login(TestPins.other(shortPin));
            assertEquals(new AdminAuth.WrongPin(left), r);
        }
        assertInstanceOf(AdminAuth.Locked.class, auth.login(TestPins.other(shortPin)));
        assertInstanceOf(AdminAuth.Locked.class, auth.login(shortPin), "PIN benar pun ditolak saat terkunci");

        clock.advance(Duration.ofSeconds(29));
        assertInstanceOf(AdminAuth.Locked.class, auth.login(shortPin));
        clock.advance(Duration.ofSeconds(2));
        assertInstanceOf(AdminAuth.Success.class, auth.login(shortPin));
        assertEquals(new AdminAuth.WrongPin(4), auth.login(TestPins.other(shortPin)), "hitungan reset setelah sukses");
    }

    @Test
    void tokenExpiresAfterFifteenIdleMinutesAndCanBeRevoked() throws Exception {
        AdminAuth auth = new AdminAuth(new PinStore(tmp), clock);
        String token = auth.createPin(shortPin);
        assertTrue(auth.validate(token));
        clock.advance(Duration.ofMinutes(14));
        assertTrue(auth.validate(token), "aktivitas memperpanjang");
        clock.advance(Duration.ofMinutes(14));
        assertTrue(auth.validate(token));
        clock.advance(Duration.ofMinutes(15));
        assertFalse(auth.validate(token));

        String second = ((AdminAuth.Success) auth.login(shortPin)).token();
        auth.logout(second);
        assertFalse(auth.validate(second));
        assertFalse(auth.validate(null));
        assertFalse(auth.validate("palsu"));
    }
}
