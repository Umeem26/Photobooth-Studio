package config;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Konfigurasi aplikasi. Urutan prioritas (tertinggi di bawah):
 * classpath config.properties, ./config.properties, system property vandebooth.output.dir.
 */
public final class AppConfig {

    public static final String OUTPUT_DIR_KEY = "output.dir";
    public static final String OUTPUT_DIR_SYSTEM_PROPERTY = "vandebooth.output.dir";
    static final String DEFAULT_OUTPUT_DIR = "~/VanDeBooth";

    private static AppConfig instance;

    private final Path outputDir;

    private AppConfig(Path outputDir) {
        this.outputDir = outputDir;
    }

    public static synchronized AppConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    static AppConfig load() {
        Properties props = new Properties();
        try (InputStream in = AppConfig.class.getResourceAsStream("/config.properties")) {
            if (in != null) props.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Gagal membaca config.properties bawaan", e);
        }

        Path external = Path.of("config.properties");
        if (Files.isRegularFile(external)) {
            try (InputStream in = Files.newInputStream(external)) {
                props.load(in);
            } catch (IOException e) {
                throw new UncheckedIOException("Gagal membaca " + external.toAbsolutePath(), e);
            }
        }

        String override = System.getProperty(OUTPUT_DIR_SYSTEM_PROPERTY);
        if (override != null && !override.isBlank()) {
            props.setProperty(OUTPUT_DIR_KEY, override);
        }
        return fromProperties(props);
    }

    public static AppConfig fromProperties(Properties props) {
        String raw = props.getProperty(OUTPUT_DIR_KEY, DEFAULT_OUTPUT_DIR).trim();
        if (raw.isEmpty()) raw = DEFAULT_OUTPUT_DIR;
        return new AppConfig(expandHome(raw));
    }

    static Path expandHome(String raw) {
        String home = System.getProperty("user.home");
        String value = raw.replace("${user.home}", home);
        if (value.equals("~")) {
            value = home;
        } else if (value.startsWith("~/") || value.startsWith("~\\")) {
            value = home + value.substring(1);
        }
        return Path.of(value).toAbsolutePath().normalize();
    }

    /** Folder utama semua hasil aplikasi. */
    public Path outputDir() {
        return outputDir;
    }

    /** Folder rekaman video countdown dan video strip. */
    public Path videoDir() {
        return outputDir.resolve("videos");
    }

    /** Folder arsip sesi (SessionRepository). */
    public Path sessionsDir() {
        return outputDir.resolve("sessions");
    }
}
