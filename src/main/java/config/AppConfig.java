package config;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import template.StripLayout;

/**
 * Konfigurasi aplikasi (immutable, tervalidasi). Urutan prioritas (tertinggi di bawah):
 * classpath config.properties, ./config.properties, {@code <config dir>/config.properties}
 * (ditulis Mode Operator), system property {@code vandebooth.<key>}.
 * Perubahan saat runtime lewat {@link ConfigStore}.
 */
public final class AppConfig {

    public static final String OUTPUT_DIR_KEY = "output.dir";
    public static final String EVENT_NAME_KEY = "event.name";
    public static final String EVENT_DATE_KEY = "event.date";
    public static final String MAX_RETAKES_KEY = "maxRetakes";
    public static final String COUNTDOWN_SECONDS_KEY = "countdown.seconds";
    public static final String PAUSE_SECONDS_KEY = "capture.pauseSeconds";
    public static final String PAYMENT_ENABLED_KEY = "payment.enabled";
    public static final String PAYMENT_PRICE_KEY = "payment.price";
    public static final String SHARE_ENABLED_KEY = "share.enabled";
    public static final String SHARE_PORT_KEY = "share.port";
    public static final String SHARE_HOST_KEY = "share.host";
    public static final String SHARE_BIND_KEY = "share.bindAddress";
    public static final String SHARE_EXPIRY_KEY = "share.expiryHours";
    public static final String PRINT_PRINTER_KEY = "print.printer";
    public static final String PRINT_MAX_COPIES_KEY = "print.maxCopies";
    public static final String PRINT_LAYOUT_KEY = "print.layout";
    public static final String PRINT_PAPER_KEY = "print.paper";
    public static final String PRINT_MODE_KEY = "print.mode";
    public static final String LAYOUTS_OFFERED_KEY = "layouts.offered";
    public static final String PHOTOS_MIRROR_KEY = "photos.mirror";

    public static final String SYSTEM_PROPERTY_PREFIX = "vandebooth.";
    public static final String OUTPUT_DIR_SYSTEM_PROPERTY = SYSTEM_PROPERTY_PREFIX + OUTPUT_DIR_KEY;
    public static final String CONFIG_DIR_SYSTEM_PROPERTY = SYSTEM_PROPERTY_PREFIX + "config.dir";

    static final String DEFAULT_OUTPUT_DIR = "~/VanDeBooth";
    static final String DEFAULT_EVENT_NAME = "Sample event";
    static final int DEFAULT_MAX_RETAKES = 2;

    public static final List<String> KEYS = List.of(OUTPUT_DIR_KEY, EVENT_NAME_KEY, EVENT_DATE_KEY, MAX_RETAKES_KEY,
            COUNTDOWN_SECONDS_KEY, PAUSE_SECONDS_KEY, PAYMENT_ENABLED_KEY, PAYMENT_PRICE_KEY, SHARE_ENABLED_KEY,
            SHARE_PORT_KEY, SHARE_HOST_KEY, SHARE_BIND_KEY, SHARE_EXPIRY_KEY, PRINT_PRINTER_KEY, PRINT_MAX_COPIES_KEY,
            PRINT_LAYOUT_KEY, PRINT_PAPER_KEY, PRINT_MODE_KEY, LAYOUTS_OFFERED_KEY,
            PHOTOS_MIRROR_KEY);

    /** Kunci yang boleh diubah lewat Mode Operator. */
    public static final Set<String> EDITABLE_KEYS = Set.of(EVENT_NAME_KEY, EVENT_DATE_KEY, MAX_RETAKES_KEY,
            COUNTDOWN_SECONDS_KEY, PAUSE_SECONDS_KEY, PAYMENT_ENABLED_KEY, PAYMENT_PRICE_KEY, SHARE_ENABLED_KEY,
            SHARE_PORT_KEY, SHARE_HOST_KEY, SHARE_EXPIRY_KEY, PRINT_PRINTER_KEY, PRINT_MAX_COPIES_KEY, PRINT_LAYOUT_KEY,
            LAYOUTS_OFFERED_KEY, PHOTOS_MIRROR_KEY);

    public static final Set<Integer> SHARE_EXPIRY_CHOICES = Set.of(1, 6, 24);
    private static final Pattern HOST = Pattern.compile("[A-Za-z0-9.\\-]{0,253}");

    private static AppConfig instance;

    private final Properties values;
    private final Path outputDir;
    private final LocalDate eventDate; // null = hari ini
    private final Clock clock;

    private AppConfig(Properties values, Path outputDir, LocalDate eventDate, Clock clock) {
        this.values = values;
        this.outputDir = outputDir;
        this.eventDate = eventDate;
        this.clock = clock;
    }

    public static synchronized AppConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    public static AppConfig load() {
        return fromProperties(loadProperties(ConfigStore.defaultConfigDir()));
    }

    /** Menggabungkan semua sumber konfigurasi sesuai urutan prioritas. */
    static Properties loadProperties(Path configDir) {
        Properties props = new Properties();
        try (InputStream in = AppConfig.class.getResourceAsStream("/config.properties")) {
            if (in != null) props.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Gagal membaca config.properties bawaan", e);
        }
        loadFile(props, Path.of("config.properties"));
        if (configDir != null) loadFile(props, configDir.resolve("config.properties"));
        for (String key : KEYS) {
            String override = System.getProperty(SYSTEM_PROPERTY_PREFIX + key);
            if (override != null && !override.isBlank()) {
                props.setProperty(key, override);
            }
        }
        return props;
    }

    private static void loadFile(Properties props, Path file) {
        if (!Files.isRegularFile(file)) return;
        try (InputStream in = Files.newInputStream(file)) {
            props.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Gagal membaca " + file.toAbsolutePath(), e);
        }
    }

    public static AppConfig fromProperties(Properties props) {
        return fromProperties(props, Clock.systemDefaultZone());
    }

    /** Membuat konfigurasi tervalidasi; IllegalArgumentException berisi kunci yang salah. */
    public static AppConfig fromProperties(Properties props, Clock clock) {
        Properties v = new Properties();
        v.putAll(props);

        String rawDir = str(v, OUTPUT_DIR_KEY, DEFAULT_OUTPUT_DIR);
        if (rawDir.isEmpty()) rawDir = DEFAULT_OUTPUT_DIR;

        String name = str(v, EVENT_NAME_KEY, DEFAULT_EVENT_NAME);
        if (name.length() > 60) throw new IllegalArgumentException(EVENT_NAME_KEY + " maksimal 60 karakter");
        v.setProperty(EVENT_NAME_KEY, name);

        String rawDate = str(v, EVENT_DATE_KEY, "");
        LocalDate date = null;
        if (!rawDate.isEmpty()) {
            try {
                date = LocalDate.parse(rawDate);
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException(EVENT_DATE_KEY + " harus berformat yyyy-MM-dd: " + rawDate, e);
            }
        }

        intIn(v, MAX_RETAKES_KEY, DEFAULT_MAX_RETAKES, 0, 10);
        intIn(v, COUNTDOWN_SECONDS_KEY, 3, 2, 5);
        intIn(v, PAUSE_SECONDS_KEY, 1, 0, 5);
        bool(v, PAYMENT_ENABLED_KEY, false);
        intIn(v, PAYMENT_PRICE_KEY, 25000, 0, 100_000_000);
        bool(v, SHARE_ENABLED_KEY, true);
        intIn(v, SHARE_PORT_KEY, 8080, 0, 65535);
        String host = str(v, SHARE_HOST_KEY, "");
        if (!HOST.matcher(host).matches()) throw new IllegalArgumentException(SHARE_HOST_KEY + " tidak valid: " + host);
        v.setProperty(SHARE_HOST_KEY, host);
        v.setProperty(SHARE_BIND_KEY, str(v, SHARE_BIND_KEY, "0.0.0.0"));
        int expiry = intIn(v, SHARE_EXPIRY_KEY, 6, 1, 24);
        if (!SHARE_EXPIRY_CHOICES.contains(expiry)) {
            throw new IllegalArgumentException(SHARE_EXPIRY_KEY + " harus 1, 6, atau 24");
        }
        v.setProperty(PRINT_PRINTER_KEY, str(v, PRINT_PRINTER_KEY, ""));
        intIn(v, PRINT_MAX_COPIES_KEY, 2, 1, 10);
        oneOf(v, PRINT_LAYOUT_KEY, "single", Set.of("single", "two-up"));
        oneOf(v, PRINT_PAPER_KEY, "4x6", Set.of("4x6"));
        oneOf(v, PRINT_MODE_KEY, "system", Set.of("system", "file"));
        v.setProperty(LAYOUTS_OFFERED_KEY, normalizeLayouts(str(v, LAYOUTS_OFFERED_KEY, "")));
        bool(v, PHOTOS_MIRROR_KEY, true);

        return new AppConfig(v, expandHome(rawDir), date, clock);
    }

    /** Daftar id layout dipisah koma; kosong = semua. Id harus dikenal, minimal satu, urutan mengikuti layout. */
    private static String normalizeLayouts(String raw) {
        if (raw.isEmpty()) {
            return Arrays.stream(StripLayout.values()).map(StripLayout::id).collect(Collectors.joining(","));
        }
        Set<String> wanted = new HashSet<>();
        for (String part : raw.split(",")) {
            String id = part.trim();
            if (id.isEmpty()) continue;
            if (StripLayout.byId(id).isEmpty()) throw new IllegalArgumentException(LAYOUTS_OFFERED_KEY + " berisi layout tidak dikenal: " + id);
            wanted.add(id);
        }
        if (wanted.isEmpty()) throw new IllegalArgumentException(LAYOUTS_OFFERED_KEY + " minimal satu layout");
        return Arrays.stream(StripLayout.values()).map(StripLayout::id).filter(wanted::contains)
                .collect(Collectors.joining(","));
    }

    private static String str(Properties v, String key, String def) {
        return v.getProperty(key, def).trim();
    }

    private static int intIn(Properties v, String key, int def, int min, int max) {
        String raw = str(v, key, String.valueOf(def));
        int value;
        try {
            value = Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " harus bilangan bulat: " + raw, e);
        }
        if (value < min || value > max) {
            throw new IllegalArgumentException(key + " harus di antara " + min + " dan " + max + ": " + value);
        }
        v.setProperty(key, String.valueOf(value));
        return value;
    }

    private static void bool(Properties v, String key, boolean def) {
        String raw = str(v, key, String.valueOf(def)).toLowerCase();
        if (!raw.equals("true") && !raw.equals("false")) {
            throw new IllegalArgumentException(key + " harus true atau false: " + raw);
        }
        v.setProperty(key, raw);
    }

    private static void oneOf(Properties v, String key, String def, Set<String> allowed) {
        String raw = str(v, key, def);
        if (!allowed.contains(raw)) throw new IllegalArgumentException(key + " harus salah satu dari " + allowed);
        v.setProperty(key, raw);
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

    /** Salinan nilai mentah tervalidasi (untuk ConfigStore). */
    Properties values() {
        Properties copy = new Properties();
        copy.putAll(values);
        return copy;
    }

    /** Nilai mentah satu kunci (sudah dinormalisasi). */
    public String raw(String key) {
        return values.getProperty(key, "");
    }

    private int integer(String key) {
        return Integer.parseInt(values.getProperty(key));
    }

    // ------------------------------------------------------------------ folder

    /** Folder utama semua hasil aplikasi. */
    public Path outputDir() { return outputDir; }

    /** Folder rekaman video countdown dan video strip. */
    public Path videoDir() { return outputDir.resolve("videos"); }

    /** Folder arsip sesi (SessionRepository). */
    public Path sessionsDir() { return outputDir.resolve("sessions"); }

    /** Folder hasil export lokal. */
    public Path exportsDir() { return outputDir.resolve("exports"); }

    /** Folder halaman siap cetak untuk print.mode=file. */
    public Path printQueueDir() { return outputDir.resolve("print-queue"); }

    // ------------------------------------------------------------------ event dan foto

    /** Nama acara untuk chip layar sambut dan caption strip (boleh kosong). */
    public String eventName() { return values.getProperty(EVENT_NAME_KEY); }

    /** Tanggal acara; bila tidak diatur, tanggal hari ini. */
    public LocalDate eventDate() { return eventDate != null ? eventDate : LocalDate.now(clock); }

    /** Batas retake per sesi (semua foto). */
    public int maxRetakes() { return integer(MAX_RETAKES_KEY); }

    public int countdownSeconds() { return integer(COUNTDOWN_SECONDS_KEY); }

    public int pauseSeconds() { return integer(PAUSE_SECONDS_KEY); }

    /** Foto baru dicermin agar sama dengan preview (preview selalu dicermin). */
    public boolean mirrorPhotos() { return Boolean.parseBoolean(values.getProperty(PHOTOS_MIRROR_KEY)); }

    // ------------------------------------------------------------------ pembayaran

    public boolean paymentEnabled() { return Boolean.parseBoolean(values.getProperty(PAYMENT_ENABLED_KEY)); }

    public int paymentPrice() { return integer(PAYMENT_PRICE_KEY); }

    // ------------------------------------------------------------------ berbagi

    public boolean shareEnabled() { return Boolean.parseBoolean(values.getProperty(SHARE_ENABLED_KEY)); }

    public int sharePort() { return integer(SHARE_PORT_KEY); }

    /** Host override untuk URL berbagi (kosong = deteksi otomatis). */
    public String shareHost() { return values.getProperty(SHARE_HOST_KEY); }

    public String shareBindAddress() { return values.getProperty(SHARE_BIND_KEY); }

    public int shareExpiryHours() { return integer(SHARE_EXPIRY_KEY); }

    // ------------------------------------------------------------------ cetak

    /** Nama printer (kosong = default sistem). */
    public String printPrinter() { return values.getProperty(PRINT_PRINTER_KEY); }

    public int printMaxCopies() { return integer(PRINT_MAX_COPIES_KEY); }

    public boolean printTwoUp() { return "two-up".equals(values.getProperty(PRINT_LAYOUT_KEY)); }

    /** Id layout yang ditawarkan ke tamu (urutan tetap, minimal satu). */
    public List<String> layoutsOffered() { return List.of(values.getProperty(LAYOUTS_OFFERED_KEY).split(",")); }

    public boolean printToFile() { return "file".equals(values.getProperty(PRINT_MODE_KEY)); }
}
