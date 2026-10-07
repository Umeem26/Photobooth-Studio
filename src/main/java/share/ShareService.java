package share;

import config.AppConfig;
import config.ConfigStore;
import exception.BoothException;
import exception.BoothException.Kind;
import utils.QrCodeGenerator;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import javax.imageio.ImageIO;

/**
 * Mengelola ShareServer sesuai konfigurasi (start, restart otomatis saat share.enabled,
 * share.port, atau share.bindAddress berubah) dan membuat link + QR per sesi.
 */
public class ShareService implements AutoCloseable {

    public static final int RATE_LIMIT_PER_MINUTE = 120;
    static final int QR_SIZE = 400;

    /** URL unduh dan QR PNG (base64). */
    public record ShareLink(String url, String qrPng) { }

    private final ConfigStore configStore;
    private final Path sessionsDir;
    private final ShareRegistry registry;
    private final Clock clock;

    private ShareServer server;
    private String runningKey;
    private String lastError;

    public ShareService(ConfigStore configStore, Path sessionsDir, Clock clock) {
        this.configStore = configStore;
        this.sessionsDir = sessionsDir;
        this.clock = clock;
        this.registry = new ShareRegistry(clock);
        configStore.addListener(c -> reconcile());
    }

    public ShareRegistry registry() {
        return registry;
    }

    /** Menyalakan/mematikan/me-restart server agar sesuai konfigurasi terkini. */
    public synchronized void reconcile() {
        AppConfig c = configStore.current();
        String key = c.shareEnabled() ? c.shareBindAddress() + ":" + c.sharePort() : null;
        if (Objects.equals(key, runningKey) && (key == null || server != null)) return;
        stopServer();
        runningKey = key;
        if (key == null) return;
        try {
            server = new ShareServer(registry, sessionsDir, new RateLimiter(RATE_LIMIT_PER_MINUTE, clock),
                    c.shareBindAddress(), c.sharePort());
            server.start();
            lastError = null;
        } catch (IOException | RuntimeException e) {
            server = null;
            lastError = "Port " + c.sharePort() + " tidak bisa dipakai: " + e.getMessage();
        }
    }

    private void stopServer() {
        if (server != null) {
            server.stop();
            server = null;
        }
    }

    public synchronized boolean running() {
        return server != null;
    }

    public synchronized Optional<Integer> port() {
        return server == null ? Optional.empty() : Optional.of(server.port());
    }

    public synchronized String lastError() {
        return lastError;
    }

    /** Host untuk URL: share.host bila diisi, selain itu IPv4 privat terdeteksi. */
    public Optional<String> host() {
        String override = configStore.current().shareHost();
        return override.isEmpty() ? NetworkAddress.detectPrivateIPv4() : Optional.of(override);
    }

    public Optional<String> detectedHost() {
        return NetworkAddress.detectPrivateIPv4();
    }

    /** http://host:port bila berbagi aktif dan bisa dijangkau. */
    public synchronized Optional<String> baseUrl() {
        if (server == null) return Optional.empty();
        return host().map(h -> "http://" + h + ":" + server.port());
    }

    public ShareLink linkForSession(String sessionId) throws BoothException {
        return link(sessionId);
    }

    /** Link ke halaman uji ("Sharing works.") untuk panel Sharing. */
    public ShareLink testLink() throws BoothException {
        return link(null);
    }

    private ShareLink link(String sessionId) throws BoothException {
        if (!configStore.current().shareEnabled()) {
            throw new BoothException(Kind.CONFLICT, "Berbagi dinonaktifkan");
        }
        String base = baseUrl().orElseThrow(() -> new BoothException(Kind.CONFLICT,
                running() ? "Tidak ada jaringan lokal terdeteksi" : "Server berbagi tidak berjalan"));
        ShareRegistry.Entry entry = registry.linkFor(sessionId,
                Duration.ofHours(configStore.current().shareExpiryHours()));
        String url = base + "/s/" + entry.token();
        return new ShareLink(url, qrBase64(url));
    }

    public static String qrBase64(String text) throws BoothException {
        try {
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            ImageIO.write(QrCodeGenerator.generate(text, QR_SIZE), "png", png);
            return Base64.getEncoder().encodeToString(png.toByteArray());
        } catch (Exception e) {
            throw new BoothException(Kind.CONFLICT, "QR gagal dibuat");
        }
    }

    public void revokeSession(String sessionId) {
        registry.revokeSession(sessionId);
    }

    @Override
    public synchronized void close() {
        stopServer();
        runningKey = null;
    }
}
