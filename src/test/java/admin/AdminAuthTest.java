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

    @Test
    void noDefaultPinAndPinStoredAsSaltedHash() throws Exception {
        PinStore pins = new PinStore(tmp);
        assertFalse(pins.isSet(), "tidak boleh ada PIN bawaan");
        assertFalse(pins.verify("0000"));
        assertFalse(pins.verify("1234"));

        assertThrows(IllegalArgumentException.class, () -> pins.create("12"));
        assertThrows(IllegalArgumentException.class, () -> pins.create("123456789"));
        assertThrows(IllegalArgumentException.class, () -> pins.create("12a4"));
        pins.create("482915");
        assertThrows(IllegalStateException.class, () -> pins.create("111111"));

        String stored = Files.readString(tmp.resolve("operator-pin.properties"));
        assertFalse(stored.contains("482915"), "PIN tidak boleh disimpan polos");
        assertTrue(stored.contains("PBKDF2WithHmacSHA256"));
        assertTrue(pins.verify("482915"));
        assertFalse(pins.verify("482916"));

        PinStore other = new PinStore(tmp.resolve("lain"));
        other.create("482915");
        assertNotEquals(stored.lines().filter(l -> l.startsWith("hash=")).findFirst(),
                Files.readString(tmp.resolve("lain").resolve("operator-pin.properties"))
                        .lines().filter(l -> l.startsWith("hash=")).findFirst(), "salt acak per instalasi");
    }

    @Test
    void lockoutAfterFiveWrongAttemptsForThirtySeconds() throws Exception {
        AdminAuth auth = new AdminAuth(new PinStore(tmp), clock);
        auth.createPin("4829");

        for (int left = 4; left >= 1; left--) {
            AdminAuth.LoginResult r = auth.login("0000");
            assertEquals(new AdminAuth.WrongPin(left), r);
        }
        assertInstanceOf(AdminAuth.Locked.class, auth.login("0000"));
        assertInstanceOf(AdminAuth.Locked.class, auth.login("4829"), "PIN benar pun ditolak saat terkunci");

        clock.advance(Duration.ofSeconds(29));
        assertInstanceOf(AdminAuth.Locked.class, auth.login("4829"));
        clock.advance(Duration.ofSeconds(2));
        assertInstanceOf(AdminAuth.Success.class, auth.login("4829"));
        assertEquals(new AdminAuth.WrongPin(4), auth.login("1111"), "hitungan reset setelah sukses");
    }

    @Test
    void tokenExpiresAfterFifteenIdleMinutesAndCanBeRevoked() throws Exception {
        AdminAuth auth = new AdminAuth(new PinStore(tmp), clock);
        String token = auth.createPin("4829");
        assertTrue(auth.validate(token));
        clock.advance(Duration.ofMinutes(14));
        assertTrue(auth.validate(token), "aktivitas memperpanjang");
        clock.advance(Duration.ofMinutes(14));
        assertTrue(auth.validate(token));
        clock.advance(Duration.ofMinutes(15));
        assertFalse(auth.validate(token));

        String second = ((AdminAuth.Success) auth.login("4829")).token();
        auth.logout(second);
        assertFalse(auth.validate(second));
        assertFalse(auth.validate(null));
        assertFalse(auth.validate("palsu"));
    }
}
