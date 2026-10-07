package config;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Properties;

/**
 * Konfigurasi aplikasi. Urutan prioritas (tertinggi di bawah):
 * classpath config.properties, ./config.properties, system property {@code vandebooth.<key>}
 * (mis. -Dvandebooth.output.dir, -Dvandebooth.event.name).
 */
public final class AppConfig {

    public static final String OUTPUT_DIR_KEY = "output.dir";
    public static final String EVENT_NAME_KEY = "event.name";
    public static final String EVENT_DATE_KEY = "event.date";
    public static final String MAX_RETAKES_KEY = "maxRetakes";
    public static final String SYSTEM_PROPERTY_PREFIX = "vandebooth.";
    public static final String OUTPUT_DIR_SYSTEM_PROPERTY = SYSTEM_PROPERTY_PREFIX + OUTPUT_DIR_KEY;

    static final String DEFAULT_OUTPUT_DIR = "~/VanDeBooth";
    static final String DEFAULT_EVENT_NAME = "Sample event";
    static final int DEFAULT_MAX_RETAKES = 2;

    private static final List<String> KEYS = List.of(OUTPUT_DIR_KEY, EVENT_NAME_KEY, EVENT_DATE_KEY, MAX_RETAKES_KEY);

    private static AppConfig instance;

    private final Path outputDir;
    private final String eventName;
    private final LocalDate eventDate; // null = hari ini
    private final int maxRetakes;
    private final Clock clock;

    private AppConfig(Path outputDir, String eventName, LocalDate eventDate, int maxRetakes, Clock clock) {
        this.outputDir = outputDir;
        this.eventName = eventName;
        this.eventDate = eventDate;
        this.maxRetakes = maxRetakes;
        this.clock = clock;
    }

    public static synchronized AppConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    public static AppConfig load() {
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

        for (String key : KEYS) {
            String override = System.getProperty(SYSTEM_PROPERTY_PREFIX + key);
            if (override != null && !override.isBlank()) {
                props.setProperty(key, override);
            }
        }
        return fromProperties(props);
    }

    public static AppConfig fromProperties(Properties props) {
        return fromProperties(props, Clock.systemDefaultZone());
    }

    public static AppConfig fromProperties(Properties props, Clock clock) {
        String rawDir = props.getProperty(OUTPUT_DIR_KEY, DEFAULT_OUTPUT_DIR).trim();
        if (rawDir.isEmpty()) rawDir = DEFAULT_OUTPUT_DIR;

        String name = props.getProperty(EVENT_NAME_KEY, DEFAULT_EVENT_NAME).trim();

        String rawDate = props.getProperty(EVENT_DATE_KEY, "").trim();
        LocalDate date = null;
        if (!rawDate.isEmpty()) {
            try {
                date = LocalDate.parse(rawDate);
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException(EVENT_DATE_KEY + " harus berformat yyyy-MM-dd: " + rawDate, e);
            }
        }

        String rawRetakes = props.getProperty(MAX_RETAKES_KEY, String.valueOf(DEFAULT_MAX_RETAKES)).trim();
        int retakes;
        try {
            retakes = Integer.parseInt(rawRetakes);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(MAX_RETAKES_KEY + " harus bilangan bulat: " + rawRetakes, e);
        }
        if (retakes < 0) {
            throw new IllegalArgumentException(MAX_RETAKES_KEY + " tidak boleh negatif: " + retakes);
        }

        return new AppConfig(expandHome(rawDir), name, date, retakes, clock);
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

    /** Folder hasil export lokal. */
    public Path exportsDir() {
        return outputDir.resolve("exports");
    }

    /** Nama acara untuk chip layar sambut dan caption strip (boleh kosong). */
    public String eventName() {
        return eventName;
    }

    /** Tanggal acara; bila tidak diatur, tanggal hari ini. */
    public LocalDate eventDate() {
        return eventDate != null ? eventDate : LocalDate.now(clock);
    }

    /** Batas retake per sesi (semua foto). */
    public int maxRetakes() {
        return maxRetakes;
    }
}
