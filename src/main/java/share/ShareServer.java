package share;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Server unduh untuk tamu di jaringan lokal. Terpisah dari API sidecar: hanya GET,
 * hanya tiga bentuk path di bawah /s/{token}, tanpa directory listing, tanpa API lain.
 * File dibaca hanya dari folder sesi milik token (nama file dibentuk server, bukan dari path).
 */
public final class ShareServer {

    private static final String TOKEN = "([A-Za-z0-9_-]{22})";
    private static final Pattern PAGE = Pattern.compile("^/s/" + TOKEN + "/?$");
    private static final Pattern STRIP = Pattern.compile("^/s/" + TOKEN + "/strip\\.png$");
    private static final Pattern PHOTO = Pattern.compile("^/s/" + TOKEN + "/photo/([1-9])\\.jpg$");

    private final ShareRegistry registry;
    private final Path sessionsDir;
    private final RateLimiter limiter;
    private final HttpServer http;
    private final ExecutorService workers;

    public ShareServer(ShareRegistry registry, Path sessionsDir, RateLimiter limiter, String bindAddress, int port)
            throws IOException {
        this.registry = registry;
        this.sessionsDir = sessionsDir.toAbsolutePath().normalize();
        this.limiter = limiter;
        this.http = HttpServer.create(new InetSocketAddress(InetAddress.getByName(bindAddress), port), 0);
        this.workers = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "share-http");
            t.setDaemon(true);
            return t;
        });
        http.setExecutor(workers);
        http.createContext("/", this::handle);
    }

    public void start() {
        http.start();
    }

    public int port() {
        return http.getAddress().getPort();
    }

    public void stop() {
        http.stop(0);
        workers.shutdownNow();
    }

    private void handle(HttpExchange ex) throws IOException {
        try (ex) {
            var h = ex.getResponseHeaders();
            h.set("X-Content-Type-Options", "nosniff");
            h.set("Referrer-Policy", "no-referrer");
            h.set("Cache-Control", "no-store");
            h.set("Content-Security-Policy", "default-src 'none'; img-src 'self'; style-src 'unsafe-inline'");

            String ip = ex.getRemoteAddress().getAddress().getHostAddress();
            if (!limiter.allow(ip)) {
                sendText(ex, 429, "Too many requests. Wait a minute, then try again.");
                return;
            }
            if (!"GET".equals(ex.getRequestMethod())) {
                notFound(ex);
                return;
            }
            // Path mentah (belum di-decode) supaya %2e%2e dan sejenisnya tidak pernah cocok
            String path = ex.getRequestURI().getRawPath();
            Matcher m;
            if ((m = PAGE.matcher(path)).matches()) {
                Optional<ShareRegistry.Entry> e = registry.resolve(m.group(1));
                if (e.isEmpty()) {
                    notFound(ex);
                } else if (e.get().isTest()) {
                    sendHtml(ex, SharePage.testPage());
                } else {
                    sendHtml(ex, SharePage.download(m.group(1), countPhotos(e.get().sessionId())));
                }
            } else if ((m = STRIP.matcher(path)).matches()) {
                serveFile(ex, m.group(1), "strip.png", "image/png");
            } else if ((m = PHOTO.matcher(path)).matches()) {
                serveFile(ex, m.group(1), "frame_" + m.group(2) + ".jpg", "image/jpeg");
            } else {
                notFound(ex);
            }
        }
    }

    private Optional<Path> sessionDir(String sessionId) {
        if (sessionId == null) return Optional.empty();
        Path dir = sessionsDir.resolve(sessionId).normalize();
        return dir.getParent() != null && dir.getParent().equals(sessionsDir) && Files.isDirectory(dir)
                ? Optional.of(dir) : Optional.empty();
    }

    private int countPhotos(String sessionId) {
        Optional<Path> dir = sessionDir(sessionId);
        if (dir.isEmpty()) return 0;
        int n = 0;
        while (n < 9 && Files.isRegularFile(dir.get().resolve("frame_" + (n + 1) + ".jpg"))) n++;
        return n;
    }

    private void serveFile(HttpExchange ex, String token, String fileName, String type) throws IOException {
        Optional<Path> dir = registry.resolve(token).flatMap(e -> sessionDir(e.sessionId()));
        Path file = dir.map(d -> d.resolve(fileName).normalize()).orElse(null);
        if (file == null || !file.getParent().equals(dir.get()) || !Files.isRegularFile(file)) {
            notFound(ex);
            return;
        }
        byte[] bytes = Files.readAllBytes(file);
        ex.getResponseHeaders().set("Content-Type", type);
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static void notFound(HttpExchange ex) throws IOException {
        byte[] body = SharePage.notFound().getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        ex.sendResponseHeaders(404, body.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(body);
        }
    }

    private static void sendHtml(HttpExchange ex, String html) throws IOException {
        byte[] body = html.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        ex.sendResponseHeaders(200, body.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(body);
        }
    }

    private static void sendText(HttpExchange ex, int status, String text) throws IOException {
        byte[] body = text.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        ex.sendResponseHeaders(status, body.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(body);
        }
    }
}
