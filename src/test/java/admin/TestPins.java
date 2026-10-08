package admin;

import java.security.SecureRandom;

/** PIN acak untuk tes, dibuat saat runtime (tidak ada PIN tertulis di repo). */
public final class TestPins {

    private static final SecureRandom RANDOM = new SecureRandom();

    private TestPins() {
    }

    public static String random(int length) {
        StringBuilder b = new StringBuilder(length);
        for (int i = 0; i < length; i++) b.append(RANDOM.nextInt(10));
        return b.toString();
    }

    /** PIN dengan panjang sama yang pasti berbeda dari {@code pin}. */
    public static String other(String pin) {
        char last = pin.charAt(pin.length() - 1);
        return pin.substring(0, pin.length() - 1) + (char) ('0' + (last - '0' + 1) % 10);
    }
}
