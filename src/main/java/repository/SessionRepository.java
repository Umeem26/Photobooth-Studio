package repository;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.imageio.ImageIO;

/**
 * Menyimpan tiap sesi ke {@code <sessionsDir>/<timestamp>/} berisi
 * frame_N.png, strip.png, dan meta.properties.
 */
public class SessionRepository {

    static final DateTimeFormatter FOLDER_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");

    private final Path sessionsDir;
    private final Clock clock;

    public SessionRepository(Path sessionsDir) {
        this(sessionsDir, Clock.systemDefaultZone());
    }

    public SessionRepository(Path sessionsDir, Clock clock) {
        if (sessionsDir == null || clock == null) {
            throw new IllegalArgumentException("sessionsDir dan clock wajib diisi");
        }
        this.sessionsDir = sessionsDir;
        this.clock = clock;
    }

    public Path getSessionsDir() {
        return sessionsDir;
    }

    /**
     * Menyimpan sesi dan mengembalikan folder sesinya.
     * Bila penulisan gagal, folder sesi yang setengah jadi dihapus.
     */
    public Path save(SessionRecord record) throws IOException {
        LocalDateTime now = LocalDateTime.now(clock);
        Files.createDirectories(sessionsDir);
        Path dir = createUniqueDir(now.format(FOLDER_FORMAT));
        try {
            for (int i = 0; i < record.frames().size(); i++) {
                writePng(record.frames().get(i), dir.resolve("frame_" + (i + 1) + ".png"));
            }
            writePng(record.strip(), dir.resolve("strip.png"));
            writeMeta(record, now, dir.resolve("meta.properties"));
            return dir;
        } catch (IOException | RuntimeException e) {
            deleteQuietly(dir);
            throw e;
        }
    }

    // --- API inkremental untuk sidecar: sesi dibuat dulu, file ditulis bertahap ---

    private static final Pattern SESSION_ID = Pattern.compile("\\d{8}_\\d{6}_\\d{3}(_\\d+)?");
    private static final Pattern FILE_NAME = Pattern.compile("[a-z0-9_]+\\.(jpg|png)");

    /** Membuat folder sesi baru dengan meta awal. ID sesi = nama folder. */
    public String createSession(Map<String, String> meta) throws IOException {
        LocalDateTime now = LocalDateTime.now(clock);
        Files.createDirectories(sessionsDir);
        Path dir = createUniqueDir(now.format(FOLDER_FORMAT));
        Properties props = new Properties();
        props.setProperty("createdAt", now.toString());
        props.putAll(meta);
        storeMeta(dir, props);
        return dir.getFileName().toString();
    }

    /** true bila ID berformat sesi yang sah (mencegah path traversal). */
    public static boolean isValidSessionId(String id) {
        return id != null && SESSION_ID.matcher(id).matches();
    }

    public Path sessionDir(String id) {
        if (!isValidSessionId(id)) {
            throw new IllegalArgumentException("ID sesi tidak sah: " + id);
        }
        return sessionsDir.resolve(id);
    }

    public boolean exists(String id) {
        return isValidSessionId(id) && Files.isDirectory(sessionsDir.resolve(id));
    }

    /** Menulis/mengganti satu file di folder sesi (nama dibatasi [a-z0-9_].jpg|png). */
    public Path writeFile(String id, String fileName, byte[] data) throws IOException {
        if (!FILE_NAME.matcher(fileName).matches()) {
            throw new IllegalArgumentException("Nama file tidak sah: " + fileName);
        }
        Path target = sessionDir(id).resolve(fileName);
        Path tmp = target.resolveSibling(fileName + ".tmp");
        Files.write(tmp, data);
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
        return target;
    }

    public Properties readMeta(String id) throws IOException {
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(sessionDir(id).resolve("meta.properties"))) {
            props.load(in);
        }
        return props;
    }

    /** Menggabungkan nilai baru ke meta.properties sesi. */
    public void updateMeta(String id, Map<String, String> values) throws IOException {
        Properties props = readMeta(id);
        props.putAll(values);
        storeMeta(sessionDir(id), props);
    }

    private static void storeMeta(Path dir, Properties props) throws IOException {
        try (OutputStream out = Files.newOutputStream(dir.resolve("meta.properties"))) {
            props.store(out, "Van de Booth session");
        }
    }

    private Path createUniqueDir(String baseName) throws IOException {
        for (int n = 1; ; n++) {
            Path candidate = sessionsDir.resolve(n == 1 ? baseName : baseName + "_" + n);
            try {
                return Files.createDirectory(candidate);
            } catch (FileAlreadyExistsException e) {
                // timestamp sama, coba akhiran berikutnya
            }
        }
    }

    private static void writePng(BufferedImage image, Path target) throws IOException {
        if (image == null) {
            throw new IOException("Gambar kosong untuk " + target.getFileName());
        }
        if (!ImageIO.write(image, "png", target.toFile())) {
            throw new IOException("Tidak ada writer PNG untuk " + target.getFileName());
        }
    }

    private static void writeMeta(SessionRecord record, LocalDateTime createdAt, Path target) throws IOException {
        Properties meta = new Properties();
        meta.setProperty("templateId", record.templateId());
        meta.setProperty("frameCount", String.valueOf(record.frames().size()));
        meta.setProperty("createdAt", createdAt.toString());
        meta.setProperty("strip.width", String.valueOf(record.strip().getWidth()));
        meta.setProperty("strip.height", String.valueOf(record.strip().getHeight()));
        if (record.video() != null) {
            meta.setProperty("video", record.video().toAbsolutePath().toString());
        }
        try (OutputStream out = Files.newOutputStream(target)) {
            meta.store(out, "Van de Booth session");
        }
    }

    private static void deleteQuietly(Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best effort
                }
            });
        } catch (IOException ignored) {
            // best effort
        }
    }
}
