package repository;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Properties;
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
