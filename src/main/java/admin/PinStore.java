package admin;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Properties;
import java.util.regex.Pattern;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * PIN operator disimpan sebagai hash PBKDF2-HMAC-SHA256 + salt acak di
 * {@code <folder config pengguna>/operator-pin.properties}. Tidak ada PIN bawaan:
 * sebelum operator membuatnya, {@link #isSet()} bernilai false.
 */
public final class PinStore {

    public static final Pattern PIN = Pattern.compile("\\d{4,8}");
    static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    static final int ITERATIONS = 120_000;
    static final int KEY_BITS = 256;

    private final Path file;
    private final SecureRandom random = new SecureRandom();

    public PinStore(Path configDir) {
        this.file = configDir.resolve("operator-pin.properties");
    }

    public synchronized boolean isSet() {
        return Files.isRegularFile(file);
    }

    /** Membuat PIN pertama. IllegalStateException bila sudah ada, IllegalArgumentException bila format salah. */
    public synchronized void create(String pin) throws IOException {
        if (isSet()) throw new IllegalStateException("PIN sudah dibuat");
        if (pin == null || !PIN.matcher(pin).matches()) {
            throw new IllegalArgumentException("PIN harus 4-8 digit");
        }
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        Properties p = new Properties();
        p.setProperty("algorithm", ALGORITHM);
        p.setProperty("iterations", String.valueOf(ITERATIONS));
        p.setProperty("salt", Base64.getEncoder().encodeToString(salt));
        p.setProperty("hash", Base64.getEncoder().encodeToString(hash(pin, salt, ITERATIONS)));
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try (OutputStream out = Files.newOutputStream(tmp)) {
            p.store(out, "Van de Booth operator PIN (PBKDF2 hash, bukan PIN)");
        }
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    /** Verifikasi dengan perbandingan waktu-konstan. */
    public synchronized boolean verify(String pin) throws IOException {
        if (!isSet() || pin == null || !PIN.matcher(pin).matches()) return false;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            p.load(in);
        }
        byte[] salt = Base64.getDecoder().decode(p.getProperty("salt", ""));
        byte[] expected = Base64.getDecoder().decode(p.getProperty("hash", ""));
        int iterations = Integer.parseInt(p.getProperty("iterations", String.valueOf(ITERATIONS)));
        return MessageDigest.isEqual(expected, hash(pin, salt, iterations));
    }

    private static byte[] hash(String pin, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(pin.toCharArray(), salt, iterations, KEY_BITS);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2 tidak tersedia", e);
        } finally {
            spec.clearPassword();
        }
    }
}
