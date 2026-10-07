package repository;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;
import javax.imageio.ImageIO;

class SessionRepositoryTest {

    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-10-07T10:15:30.123Z"), ZoneOffset.UTC);

    @TempDir
    Path tmp;

    private static BufferedImage img(int w, int h) {
        return new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
    }

    private static Properties readMeta(Path dir) throws IOException {
        Properties meta = new Properties();
        try (InputStream in = Files.newInputStream(dir.resolve("meta.properties"))) {
            meta.load(in);
        }
        return meta;
    }

    @Test
    void savesFramesStripAndMetaInTimestampFolder() throws Exception {
        Path sessions = tmp.resolve("sessions");
        SessionRepository repo = new SessionRepository(sessions, FIXED);
        Path video = tmp.resolve("strip.mp4");

        Path dir = repo.save(new SessionRecord("TPL-V-2", List.of(img(40, 30), img(40, 30)), img(200, 300), video));

        assertEquals(sessions.resolve("20261007_101530_123"), dir);
        assertTrue(Files.isRegularFile(dir.resolve("frame_1.png")));
        assertTrue(Files.isRegularFile(dir.resolve("frame_2.png")));
        BufferedImage strip = ImageIO.read(dir.resolve("strip.png").toFile());
        assertEquals(200, strip.getWidth());
        assertEquals(300, strip.getHeight());

        Properties meta = readMeta(dir);
        assertEquals("TPL-V-2", meta.getProperty("templateId"));
        assertEquals("2", meta.getProperty("frameCount"));
        assertEquals("2026-10-07T10:15:30.123", meta.getProperty("createdAt"));
        assertEquals(video.toAbsolutePath().toString(), meta.getProperty("video"));
    }

    @Test
    void sameTimestampGetsUniqueFolder() throws Exception {
        SessionRepository repo = new SessionRepository(tmp, FIXED);
        SessionRecord record = new SessionRecord("TPL-H-2", List.of(img(10, 10)), img(20, 10), null);

        Path first = repo.save(record);
        Path second = repo.save(record);

        assertNotEquals(first, second);
        assertEquals("20261007_101530_123_2", second.getFileName().toString());
        assertNull(readMeta(second).getProperty("video"));
    }

    @Test
    void failedWriteLeavesNoHalfFinishedFolder() throws Exception {
        SessionRepository repo = new SessionRepository(tmp, FIXED);
        SessionRecord record = new SessionRecord("TPL-V-2", Arrays.asList(img(10, 10), null), img(20, 10), null);

        assertThrows(IOException.class, () -> repo.save(record));
        try (Stream<Path> children = Files.list(tmp)) {
            assertEquals(0, children.count());
        }
    }

    @Test
    void recordValidatesRequiredFields() {
        assertThrows(IllegalArgumentException.class, () -> new SessionRecord(" ", List.of(), img(1, 1), null));
        assertThrows(IllegalArgumentException.class, () -> new SessionRecord("TPL-V-2", List.of(), null, null));
    }
}
