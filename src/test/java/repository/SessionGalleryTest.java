package repository;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;

class SessionGalleryTest {

    @TempDir
    Path tmp;

    private static java.time.Clock at(String iso) {
        return java.time.Clock.fixed(Instant.parse(iso), ZoneOffset.UTC);
    }

    @Test
    void listsNewestFirstWithMetaAndIgnoresForeignFolders() throws Exception {
        Path sessions = tmp.resolve("sessions");
        String old = new SessionRepository(sessions, at("2026-10-01T09:00:00Z")).createSession(Map.of("layout", "vertical-4"));
        SessionRepository repo = new SessionRepository(sessions, at("2026-10-12T09:00:00Z"));
        String fresh = repo.createSession(Map.of("layout", "horizontal-3", "status", "composed"));
        repo.writeFile(fresh, "frame_1.jpg", new byte[] {1});
        repo.writeFile(fresh, "strip.png", new byte[] {2});
        Files.createDirectories(sessions.resolve("bukan-sesi"));

        List<SessionRepository.SessionInfo> list = repo.list();
        assertEquals(List.of(fresh, old), list.stream().map(SessionRepository.SessionInfo::id).toList());
        assertEquals("composed", list.get(0).status());
        assertEquals(1, list.get(0).frames());
        assertTrue(list.get(0).hasStrip());
        assertFalse(list.get(1).hasStrip());
    }

    @Test
    void deletePurgeAndExportAll() throws Exception {
        Path sessions = tmp.resolve("sessions");
        String old = new SessionRepository(sessions, at("2026-10-01T09:00:00Z")).createSession(Map.of());
        SessionRepository repo = new SessionRepository(sessions, at("2026-10-12T09:00:00Z"));
        String a = repo.createSession(Map.of());
        String b = repo.createSession(Map.of());
        repo.writeFile(a, "strip.png", new byte[] {9});

        Path zip = repo.exportAll(tmp.resolve("exports").resolve("all.zip"));
        try (ZipFile z = new ZipFile(zip.toFile())) {
            assertNotNull(z.getEntry("sessions/" + a + "/strip.png"));
            assertNotNull(z.getEntry("sessions/" + old + "/meta.properties"));
        }

        assertTrue(repo.delete(b));
        assertFalse(repo.delete(b));
        assertFalse(repo.delete("../../etc"));
        assertFalse(Files.exists(sessions.resolve(b)));

        assertEquals(List.of(old), repo.purgeOlderThan(7));
        assertEquals(List.of(a), repo.list().stream().map(SessionRepository.SessionInfo::id).toList());
    }
}
