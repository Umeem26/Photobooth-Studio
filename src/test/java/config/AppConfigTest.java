package config;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
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
}
