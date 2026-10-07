package config;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Properties;

class AppConfigTest {

    private final Path home = Path.of(System.getProperty("user.home")).toAbsolutePath().normalize();

    @Test
    void defaultOutputDirIsVanDeBoothInHome() {
        AppConfig config = AppConfig.fromProperties(new Properties());
        assertEquals(home.resolve("VanDeBooth"), config.outputDir());
    }

    @Test
    void expandsTildeAndUserHomePlaceholder() {
        Properties p = new Properties();
        p.setProperty(AppConfig.OUTPUT_DIR_KEY, "~/Booth/Out");
        assertEquals(home.resolve("Booth").resolve("Out"), AppConfig.fromProperties(p).outputDir());

        p.setProperty(AppConfig.OUTPUT_DIR_KEY, "${user.home}/Lain");
        assertEquals(home.resolve("Lain"), AppConfig.fromProperties(p).outputDir());
    }

    @Test
    void derivedDirectoriesLiveUnderOutputDir(@TempDir Path tmp) {
        Properties p = new Properties();
        p.setProperty(AppConfig.OUTPUT_DIR_KEY, tmp.toString());
        AppConfig config = AppConfig.fromProperties(p);

        assertEquals(tmp.toAbsolutePath().normalize(), config.outputDir());
        assertEquals(config.outputDir().resolve("videos"), config.videoDir());
        assertEquals(config.outputDir().resolve("sessions"), config.sessionsDir());
    }

    @Test
    void systemPropertyOverridesFile(@TempDir Path tmp) {
        System.setProperty(AppConfig.OUTPUT_DIR_SYSTEM_PROPERTY, tmp.toString());
        try {
            assertEquals(tmp.toAbsolutePath().normalize(), AppConfig.load().outputDir());
        } finally {
            System.clearProperty(AppConfig.OUTPUT_DIR_SYSTEM_PROPERTY);
        }
    }

    @Test
    void eventAndRetakeDefaults() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-12T08:00:00Z"), ZoneOffset.UTC);
        AppConfig config = AppConfig.fromProperties(new Properties(), clock);
        assertEquals("Sample event", config.eventName());
        assertEquals(LocalDate.of(2026, 10, 12), config.eventDate());
        assertEquals(2, config.maxRetakes());
        assertEquals(config.outputDir().resolve("exports"), config.exportsDir());
    }

    @Test
    void readsEventAndRetakeValues() {
        Properties p = new Properties();
        p.setProperty(AppConfig.EVENT_NAME_KEY, "Rina & Bayu");
        p.setProperty(AppConfig.EVENT_DATE_KEY, "2026-12-31");
        p.setProperty(AppConfig.MAX_RETAKES_KEY, "0");
        AppConfig config = AppConfig.fromProperties(p);
        assertEquals("Rina & Bayu", config.eventName());
        assertEquals(LocalDate.of(2026, 12, 31), config.eventDate());
        assertEquals(0, config.maxRetakes());
    }

    @Test
    void rejectsInvalidDateAndRetakes() {
        Properties badDate = new Properties();
        badDate.setProperty(AppConfig.EVENT_DATE_KEY, "12/10/2026");
        assertThrows(IllegalArgumentException.class, () -> AppConfig.fromProperties(badDate));

        Properties negative = new Properties();
        negative.setProperty(AppConfig.MAX_RETAKES_KEY, "-1");
        assertThrows(IllegalArgumentException.class, () -> AppConfig.fromProperties(negative));

        Properties notNumber = new Properties();
        notNumber.setProperty(AppConfig.MAX_RETAKES_KEY, "dua");
        assertThrows(IllegalArgumentException.class, () -> AppConfig.fromProperties(notNumber));
    }

    @Test
    void systemPropertyOverridesEventName() {
        System.setProperty("vandebooth.event.name", "Override");
        try {
            assertEquals("Override", AppConfig.load().eventName());
        } finally {
            System.clearProperty("vandebooth.event.name");
        }
    }
}
