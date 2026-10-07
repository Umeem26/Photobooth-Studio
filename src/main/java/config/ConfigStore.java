package config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Konfigurasi yang bisa diubah saat runtime (Mode Operator). Perubahan divalidasi,
 * ditulis atomik ke {@code <config dir>/config.properties}, lalu listener diberi tahu
 * sehingga berlaku tanpa restart.
 */
public final class ConfigStore {

    private final Path configDir;           // null = tanpa persistensi (tes)
    private final Clock clock;
    private final List<Consumer<AppConfig>> listeners = new CopyOnWriteArrayList<>();
    private volatile AppConfig current;

    private ConfigStore(Path configDir, AppConfig initial, Clock clock) {
        this.configDir = configDir;
        this.current = initial;
        this.clock = clock;
    }

    /** Memuat dari semua sumber; folder config dari -Dvandebooth.config.dir atau folder config pengguna. */
    public static ConfigStore load() {
        Path dir = defaultConfigDir();
        return new ConfigStore(dir, AppConfig.fromProperties(AppConfig.loadProperties(dir)), Clock.systemDefaultZone());
    }

    /** Untuk tes: nilai awal dari Properties, perubahan ditulis ke configDir (boleh null). */
    public static ConfigStore of(Properties initial, Path configDir) {
        return of(initial, configDir, Clock.systemDefaultZone());
    }

    public static ConfigStore of(Properties initial, Path configDir, Clock clock) {
        return new ConfigStore(configDir, AppConfig.fromProperties(initial, clock), clock);
    }

    public static ConfigStore inMemory(AppConfig config) {
        return new ConfigStore(null, config, Clock.systemDefaultZone());
    }

    /** %APPDATA%\VanDeBooth (Windows), $XDG_CONFIG_HOME/vandebooth, atau ~/.config/vandebooth. */
    public static Path defaultConfigDir() {
        String override = System.getProperty(AppConfig.CONFIG_DIR_SYSTEM_PROPERTY);
        if (override != null && !override.isBlank()) return AppConfig.expandHome(override.trim());
        String appData = System.getenv("APPDATA");
        if (System.getProperty("os.name", "").toLowerCase().startsWith("windows") && appData != null) {
            return Path.of(appData, "VanDeBooth");
        }
        String xdg = System.getenv("XDG_CONFIG_HOME");
        if (xdg != null && !xdg.isBlank()) return Path.of(xdg, "vandebooth");
        return Path.of(System.getProperty("user.home"), ".config", "vandebooth");
    }

    public AppConfig current() {
        return current;
    }

    /** Folder config pengguna (null bila tanpa persistensi). */
    public Path configDir() {
        return configDir;
    }

    public void addListener(Consumer<AppConfig> listener) {
        listeners.add(listener);
    }

    /**
     * Menerapkan perubahan (hanya kunci EDITABLE). Gagal validasi -> IllegalArgumentException
     * dan konfigurasi tidak berubah.
     */
    public synchronized AppConfig update(Map<String, String> changes) throws IOException {
        for (String key : changes.keySet()) {
            if (!AppConfig.EDITABLE_KEYS.contains(key)) {
                throw new IllegalArgumentException("Kunci tidak boleh diubah: " + key);
            }
        }
        Properties merged = current.values();
        changes.forEach((k, v) -> merged.setProperty(k, v == null ? "" : v));
        AppConfig next = AppConfig.fromProperties(merged, clock); // validasi
        if (configDir != null) persist(changes, next);
        current = next;
        for (Consumer<AppConfig> l : listeners) l.accept(next);
        return next;
    }

    private void persist(Map<String, String> changes, AppConfig next) throws IOException {
        Files.createDirectories(configDir);
        Path file = configDir.resolve("config.properties");
        Properties saved = new Properties();
        if (Files.isRegularFile(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                saved.load(in);
            }
        }
        for (String key : changes.keySet()) saved.setProperty(key, next.raw(key));
        Path tmp = configDir.resolve("config.properties.tmp");
        try (OutputStream out = Files.newOutputStream(tmp)) {
            saved.store(out, "Van de Booth - diubah lewat Mode Operator");
        }
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
