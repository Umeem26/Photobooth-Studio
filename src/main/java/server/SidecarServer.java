package server;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import config.AppConfig;
import exception.BoothException;
import service.PhotoboothService;
import service.SessionManager;
import template.StripLayout;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sidecar HTTP untuk UI Electron/React. Hanya bind 127.0.0.1, setiap request
 * (kecuali preflight CORS) wajib membawa header X-Booth-Token yang benar.
 * Semua logika memakai Facade PhotoboothService.
 */
public class SidecarServer {

    public static final String TOKEN_HEADER = "X-Booth-Token";
    static final int MAX_BODY_BYTES = 25 * 1024 * 1024;

    private static final Pattern SESSION = Pattern.compile("^/api/sessions/([^/]+)$");
    private static final Pattern FRAME = Pattern.compile("^/api/sessions/([^/]+)/frames/(\\d{1,3})$");
    private static final Pattern COMPOSE = Pattern.compile("^/api/sessions/([^/]+)/compose$");
    private static final Pattern STRIP = Pattern.compile("^/api/sessions/([^/]+)/strip\\.png$");
    private static final Pattern EXPORT = Pattern.compile("^/api/sessions/([^/]+)/export$");
    private static final Pattern ABANDON = Pattern.compile("^/api/sessions/([^/]+)/abandon$");

    private final PhotoboothService service;
    private final byte[] token;
    private final HttpServer http;
    private final ExecutorService workers;
    private final Gson gson = new Gson();

    public SidecarServer(PhotoboothService service, String token, int port) throws IOException {
        if (service == null) throw new IllegalArgumentException("service wajib diisi");
        if (token == null || token.length() < 16) {
            throw new IllegalArgumentException("Token minimal 16 karakter");
        }
        this.service = service;
        this.token = token.getBytes(StandardCharsets.UTF_8);
        this.http = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
        this.workers = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "sidecar-http");
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
        try {
            workers.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static String version() {
        Properties p = new Properties();
        try (InputStream in = SidecarServer.class.getResourceAsStream("/vandebooth-version.properties")) {
            if (in != null) p.load(in);
        } catch (IOException ignored) {
            // versi tidak wajib
        }
        return p.getProperty("version", "dev");
    }

    // ---------------------------------------------------------------- routing

    private void handle(HttpExchange ex) throws IOException {
        try (ex) {
            addCorsHeaders(ex);
            if ("OPTIONS".equals(ex.getRequestMethod())) {
                ex.sendResponseHeaders(204, -1);
                return;
            }
            if (!authorized(ex)) {
                sendError(ex, 401, "unauthorized", "Token salah atau tidak ada");
                return;
            }
            try {
                route(ex);
            } catch (ApiError e) {
                sendError(ex, e.status, e.code, e.getMessage());
            } catch (BoothException e) {
                sendError(ex, e.kind().status(), e.kind().code(), e.getMessage());
            } catch (RuntimeException | IOException e) {
                sendError(ex, 500, "internal", "Kesalahan tak terduga: " + e.getClass().getSimpleName());
            }
        }
    }

    private void route(HttpExchange ex) throws IOException, BoothException, ApiError {
        String method = ex.getRequestMethod();
        String path = ex.getRequestURI().getPath();
        Matcher m;

        if (path.equals("/health")) {
            requireMethod(method, "GET");
            sendJson(ex, 200, Map.of("ok", true, "version", version()));
        } else if (path.equals("/api/config")) {
            requireMethod(method, "GET");
            sendJson(ex, 200, configBody());
        } else if (path.equals("/api/layouts")) {
            requireMethod(method, "GET");
            sendJson(ex, 200, service.getLayouts().stream().map(SidecarServer::layoutBody).toList());
        } else if (path.equals("/api/filters")) {
            requireMethod(method, "GET");
            sendJson(ex, 200, service.getFilters().stream()
                    .map(f -> Map.of("id", f.id(), "name", f.displayName())).toList());
        } else if (path.equals("/api/sessions")) {
            requireMethod(method, "POST");
            String layout = requiredString(readJson(ex), "layout");
            sendJson(ex, 201, Map.of("sessionId", service.createSession(layout)));
        } else if ((m = FRAME.matcher(path)).matches()) {
            int index = Integer.parseInt(m.group(2));
            if ("PUT".equals(method)) {
                service.putFrame(m.group(1), index, readBody(ex));
                sendJson(ex, 200, Map.of("ok", true, "index", index));
            } else {
                requireMethod(method, "GET");
                sendBytes(ex, "image/jpeg", service.getFrame(m.group(1), index));
            }
        } else if ((m = COMPOSE.matcher(path)).matches()) {
            requireMethod(method, "POST");
            String filter = requiredString(readJson(ex), "filter");
            SessionManager.ComposeResult r = service.compose(m.group(1), filter);
            sendJson(ex, 200, Map.of("stripUrl", "/api/sessions/" + r.sessionId() + "/strip.png?v=" + r.version()));
        } else if ((m = STRIP.matcher(path)).matches()) {
            requireMethod(method, "GET");
            Path strip = service.getStripPath(m.group(1));
            sendBytes(ex, "image/png", Files.readAllBytes(strip));
        } else if ((m = EXPORT.matcher(path)).matches()) {
            requireMethod(method, "POST");
            String strategy = requiredString(readJson(ex), "strategy");
            if (!"local".equals(strategy)) {
                throw new ApiError(400, "bad_request", "Strategi export tidak didukung: " + strategy);
            }
            sendJson(ex, 200, Map.of("path", service.exportLocal(m.group(1)).toString()));
        } else if ((m = ABANDON.matcher(path)).matches()) {
            requireMethod(method, "POST");
            service.abandon(m.group(1));
            sendJson(ex, 200, Map.of("ok", true));
        } else if (SESSION.matcher(path).matches()) {
            throw new ApiError(405, "method_not_allowed", "Method tidak didukung");
        } else {
            throw new ApiError(404, "not_found", "Endpoint tidak ada: " + path);
        }
    }

    private Map<String, Object> configBody() {
        AppConfig config = service.getConfig();
        Map<String, Integer> photos = new LinkedHashMap<>();
        for (StripLayout l : service.getLayouts()) photos.put(l.id(), l.photos());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("event.name", config.eventName());
        body.put("event.date", config.eventDate().toString());
        body.put("maxRetakes", config.maxRetakes());
        body.put("photosPerLayout", photos);
        return body;
    }

    private static Map<String, Object> layoutBody(StripLayout l) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", l.id());
        body.put("name", l.displayName());
        body.put("description", l.description());
        body.put("photos", l.photos());
        body.put("orientation", l.orientation().name().toLowerCase());
        return body;
    }

    // ---------------------------------------------------------------- helpers

    private boolean authorized(HttpExchange ex) {
        String given = ex.getRequestHeaders().getFirst(TOKEN_HEADER);
        return given != null && MessageDigest.isEqual(token, given.getBytes(StandardCharsets.UTF_8));
    }

    private static void addCorsHeaders(HttpExchange ex) {
        var h = ex.getResponseHeaders();
        h.set("Access-Control-Allow-Origin", "*");
        h.set("Access-Control-Allow-Headers", TOKEN_HEADER + ", Content-Type");
        h.set("Access-Control-Allow-Methods", "GET, POST, PUT, OPTIONS");
        h.set("Access-Control-Max-Age", "600");
    }

    private static void requireMethod(String actual, String expected) throws ApiError {
        if (!expected.equals(actual)) {
            throw new ApiError(405, "method_not_allowed", "Gunakan " + expected);
        }
    }

    private static byte[] readBody(HttpExchange ex) throws IOException, ApiError {
        try (InputStream in = ex.getRequestBody()) {
            byte[] body = in.readNBytes(MAX_BODY_BYTES + 1);
            if (body.length > MAX_BODY_BYTES) {
                throw new ApiError(413, "too_large", "Body terlalu besar");
            }
            return body;
        }
    }

    private static JsonObject readJson(HttpExchange ex) throws IOException, ApiError {
        String text = new String(readBody(ex), StandardCharsets.UTF_8);
        try {
            var el = JsonParser.parseString(text);
            if (!el.isJsonObject()) throw new ApiError(400, "bad_request", "Body harus objek JSON");
            return el.getAsJsonObject();
        } catch (JsonParseException | IllegalStateException e) {
            throw new ApiError(400, "bad_request", "JSON tidak valid");
        }
    }

    private static String requiredString(JsonObject json, String field) throws ApiError {
        var el = json.get(field);
        if (el == null || !el.isJsonPrimitive() || !el.getAsJsonPrimitive().isString() || el.getAsString().isBlank()) {
            throw new ApiError(400, "bad_request", "Field '" + field + "' wajib berupa string");
        }
        return el.getAsString();
    }

    private void sendJson(HttpExchange ex, int status, Object body) throws IOException {
        byte[] bytes = gson.toJson(body).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static void sendBytes(HttpExchange ex, String contentType, byte[] bytes) throws IOException {
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    private void sendError(HttpExchange ex, int status, String code, String message) throws IOException {
        sendJson(ex, status, Map.of("error", code, "message", message == null ? "" : message));
    }

    /** Error API dengan status HTTP eksplisit. */
    static final class ApiError extends Exception {
        final int status;
        final String code;

        ApiError(int status, String code, String message) {
            super(message);
            this.status = status;
            this.code = code;
        }
    }
}
