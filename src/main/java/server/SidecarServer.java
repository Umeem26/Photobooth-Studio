package server;

import admin.AdminAuth;
import admin.AdminService;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import config.AppConfig;
import exception.BoothException;
import payment.PaymentStatus;
import print.PrintManager;
import repository.SessionRepository;
import service.PhotoboothService;
import service.SessionManager;
import share.ShareService;
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
import java.util.List;
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
 * Endpoint /api/admin/* juga wajib X-Admin-Token (kecuali pin dan login).
 * Semua logika lewat Facade PhotoboothService dan AdminService.
 */
public class SidecarServer {

    public static final String TOKEN_HEADER = "X-Booth-Token";
    public static final String ADMIN_HEADER = "X-Admin-Token";
    static final int MAX_BODY_BYTES = 25 * 1024 * 1024;

    private static final String ID = "([^/]+)";
    private static final Pattern SESSION = Pattern.compile("^/api/sessions/" + ID + "$");
    private static final Pattern FRAME = Pattern.compile("^/api/sessions/" + ID + "/frames/(\\d{1,3})$");
    private static final Pattern COMPOSE = Pattern.compile("^/api/sessions/" + ID + "/compose$");
    private static final Pattern STRIP = Pattern.compile("^/api/sessions/" + ID + "/strip\\.png$");
    private static final Pattern EXPORT = Pattern.compile("^/api/sessions/" + ID + "/export$");
    private static final Pattern ABANDON = Pattern.compile("^/api/sessions/" + ID + "/abandon$");
    private static final Pattern PAYMENT = Pattern.compile("^/api/sessions/" + ID + "/payment$");
    private static final Pattern PAYMENT_SIMULATE = Pattern.compile("^/api/sessions/" + ID + "/payment/simulate$");
    private static final Pattern SHARE = Pattern.compile("^/api/sessions/" + ID + "/share$");
    private static final Pattern PRINT = Pattern.compile("^/api/sessions/" + ID + "/print$");
    private static final Pattern ADMIN_SESSION = Pattern.compile("^/api/admin/sessions/" + ID + "$");
    private static final Pattern ADMIN_THUMB = Pattern.compile("^/api/admin/sessions/" + ID + "/thumb\\.jpg$");

    private final PhotoboothService service;
    private final AdminService admin;
    private final byte[] token;
    private final HttpServer http;
    private final ExecutorService workers;
    private final Gson gson = new Gson();

    public SidecarServer(PhotoboothService service, AdminService admin, String token, int port) throws IOException {
        if (service == null || admin == null) throw new IllegalArgumentException("service dan admin wajib diisi");
        if (token == null || token.length() < 16) {
            throw new IllegalArgumentException("Token minimal 16 karakter");
        }
        this.service = service;
        this.admin = admin;
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
                String path = ex.getRequestURI().getPath();
                if (path.startsWith("/api/admin/")) routeAdmin(ex, path);
                else route(ex, path);
            } catch (ApiError e) {
                sendJson(ex, e.status, e.body());
            } catch (BoothException e) {
                sendError(ex, e.kind().status(), e.kind().code(), e.getMessage());
            } catch (RuntimeException | IOException e) {
                sendError(ex, 500, "internal", "Kesalahan tak terduga: " + e.getClass().getSimpleName());
            }
        }
    }

    private void route(HttpExchange ex, String path) throws IOException, BoothException, ApiError {
        String method = ex.getRequestMethod();
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
        } else if (path.equals("/api/printers")) {
            requireMethod(method, "GET");
            sendJson(ex, 200, printersBody());
        } else if (path.equals("/api/sessions")) {
            requireMethod(method, "POST");
            JsonObject body = readJson(ex);
            String layout = requiredString(body, "layout");
            String continueFrom = optionalString(body, "continueFrom");
            sendJson(ex, 201, Map.of("sessionId", service.createSession(layout, continueFrom)));
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
                throw ApiError.of(400, "bad_request", "Strategi export tidak didukung: " + strategy);
            }
            sendJson(ex, 200, Map.of("path", service.exportLocal(m.group(1)).toString()));
        } else if ((m = ABANDON.matcher(path)).matches()) {
            requireMethod(method, "POST");
            service.abandon(m.group(1));
            sendJson(ex, 200, Map.of("ok", true));
        } else if ((m = PAYMENT_SIMULATE.matcher(path)).matches()) {
            requireMethod(method, "POST");
            sendJson(ex, 200, Map.of("status", service.simulatePayment(m.group(1)).id()));
        } else if ((m = PAYMENT.matcher(path)).matches()) {
            if ("POST".equals(method)) {
                SessionManager.PaymentStart p = service.startPayment(m.group(1));
                sendJson(ex, 200, Map.of("status", p.status().id(), "amount", p.amount(),
                        "qrPng", ShareService.qrBase64(p.qrPayload())));
            } else {
                requireMethod(method, "GET");
                PaymentStatus status = service.paymentStatus(m.group(1));
                sendJson(ex, 200, Map.of("status", status.id(), "required", service.paymentRequired(m.group(1))));
            }
        } else if ((m = SHARE.matcher(path)).matches()) {
            requireMethod(method, "POST");
            ShareService.ShareLink link = service.share(m.group(1));
            sendJson(ex, 200, Map.of("url", link.url(), "qrPng", link.qrPng()));
        } else if ((m = PRINT.matcher(path)).matches()) {
            if ("POST".equals(method)) {
                JsonObject body = readJson(ex);
                int copies = body.has("copies") ? intField(body, "copies") : 1;
                sendJson(ex, 202, printBody(service.print(m.group(1), copies)));
            } else {
                requireMethod(method, "GET");
                sendJson(ex, 200, printBody(service.printStatus(m.group(1))));
            }
        } else if (SESSION.matcher(path).matches()) {
            throw ApiError.of(405, "method_not_allowed", "Method tidak didukung");
        } else {
            throw ApiError.of(404, "not_found", "Endpoint tidak ada: " + path);
        }
    }

    private void routeAdmin(HttpExchange ex, String path) throws IOException, BoothException, ApiError {
        String method = ex.getRequestMethod();
        AdminAuth auth = admin.auth();
        Matcher m;

        // Tanpa token admin: cek PIN, buat PIN pertama, login
        if (path.equals("/api/admin/pin")) {
            if ("GET".equals(method)) {
                sendJson(ex, 200, Map.of("set", auth.pinSet()));
                return;
            }
            requireMethod(method, "POST");
            String pin = requiredString(readJson(ex), "pin");
            try {
                sendJson(ex, 201, Map.of("token", auth.createPin(pin)));
            } catch (IllegalStateException e) {
                throw ApiError.of(409, "pin_exists", "PIN sudah dibuat");
            } catch (IllegalArgumentException e) {
                throw ApiError.of(400, "bad_request", "PIN harus 4-8 digit");
            }
            return;
        }
        if (path.equals("/api/admin/login")) {
            requireMethod(method, "POST");
            if (!auth.pinSet()) throw ApiError.of(409, "pin_not_set", "PIN belum dibuat");
            AdminAuth.LoginResult r = auth.login(requiredString(readJson(ex), "pin"));
            if (r instanceof AdminAuth.Success s) {
                sendJson(ex, 200, Map.of("token", s.token()));
            } else if (r instanceof AdminAuth.WrongPin w) {
                throw new ApiError(403, Map.of("error", "wrong_pin", "message", "PIN salah",
                        "attemptsLeft", w.attemptsLeft()));
            } else if (r instanceof AdminAuth.Locked l) {
                ex.getResponseHeaders().set("Retry-After", String.valueOf(l.retryAfterSeconds()));
                throw new ApiError(423, Map.of("error", "locked", "message", "Terlalu banyak percobaan",
                        "retryAfterSeconds", l.retryAfterSeconds()));
            }
            return;
        }

        String adminToken = ex.getRequestHeaders().getFirst(ADMIN_HEADER);
        if (!auth.validate(adminToken)) {
            throw ApiError.of(403, "admin_unauthorized", "Token admin salah atau kedaluwarsa");
        }

        if (path.equals("/api/admin/logout")) {
            requireMethod(method, "POST");
            auth.logout(adminToken);
            sendJson(ex, 200, Map.of("ok", true));
        } else if (path.equals("/api/admin/config")) {
            if ("PUT".equals(method)) {
                sendJson(ex, 200, admin.updateConfig(stringMap(readJson(ex))));
            } else {
                requireMethod(method, "GET");
                sendJson(ex, 200, admin.config());
            }
        } else if (path.equals("/api/admin/sessions")) {
            requireMethod(method, "GET");
            sendJson(ex, 200, admin.sessions().stream().map(SidecarServer::sessionBody).toList());
        } else if ((m = ADMIN_THUMB.matcher(path)).matches()) {
            requireMethod(method, "GET");
            sendBytes(ex, "image/jpeg", admin.thumbnail(m.group(1)));
        } else if ((m = ADMIN_SESSION.matcher(path)).matches()) {
            requireMethod(method, "DELETE");
            admin.deleteSession(m.group(1));
            sendJson(ex, 200, Map.of("ok", true));
        } else if (path.equals("/api/admin/export-all")) {
            requireMethod(method, "POST");
            sendJson(ex, 200, Map.of("path", admin.exportAll().toString()));
        } else if (path.equals("/api/admin/purge")) {
            requireMethod(method, "POST");
            List<String> deleted = admin.purge(intField(readJson(ex), "olderThanDays"));
            sendJson(ex, 200, Map.of("deleted", deleted.size(), "ids", deleted));
        } else if (path.equals("/api/admin/status")) {
            requireMethod(method, "GET");
            Map<String, Object> status = admin.status(version());
            status.put("printers", printersBody());
            sendJson(ex, 200, status);
        } else if (path.equals("/api/admin/share-test")) {
            requireMethod(method, "POST");
            ShareService.ShareLink link = admin.shareTest();
            sendJson(ex, 200, Map.of("url", link.url(), "qrPng", link.qrPng()));
        } else if (path.equals("/api/admin/print-test")) {
            if ("POST".equals(method)) {
                sendJson(ex, 202, printBody(admin.printTest()));
            } else {
                requireMethod(method, "GET");
                sendJson(ex, 200, printBody(admin.printTestStatus()));
            }
        } else {
            throw ApiError.of(404, "not_found", "Endpoint tidak ada: " + path);
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
        body.put("countdownSeconds", config.countdownSeconds());
        body.put("pauseSeconds", config.pauseSeconds());
        body.put("payment", Map.of("enabled", config.paymentEnabled(), "price", config.paymentPrice()));
        body.put("share", Map.of("enabled", config.shareEnabled()));
        body.put("print", Map.of("maxCopies", config.printMaxCopies()));
        return body;
    }

    private List<Map<String, Object>> printersBody() {
        return service.getPrintManager().printers().stream()
                .map(p -> Map.<String, Object>of("name", p.name(), "default", p.isDefault(), "status", p.status()))
                .toList();
    }

    private static Map<String, Object> printBody(PrintManager.JobStatus s) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", s.state().id());
        body.put("queued", s.state() == PrintManager.State.QUEUED || s.state() == PrintManager.State.PRINTING);
        body.put("copiesUsed", s.copiesUsed());
        body.put("maxCopies", s.maxCopies());
        if (s.error() != null) body.put("message", s.error());
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

    private static Map<String, Object> sessionBody(SessionRepository.SessionInfo s) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", s.id());
        body.put("status", s.status());
        body.put("layout", s.layout());
        body.put("filter", s.filter());
        body.put("createdAt", s.createdAt().toString());
        body.put("frames", s.frames());
        body.put("hasStrip", s.hasStrip());
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
        h.set("Access-Control-Allow-Headers", TOKEN_HEADER + ", " + ADMIN_HEADER + ", Content-Type");
        h.set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        h.set("Access-Control-Max-Age", "600");
    }

    private static void requireMethod(String actual, String expected) throws ApiError {
        if (!expected.equals(actual)) {
            throw ApiError.of(405, "method_not_allowed", "Gunakan " + expected);
        }
    }

    private static byte[] readBody(HttpExchange ex) throws IOException, ApiError {
        try (InputStream in = ex.getRequestBody()) {
            byte[] body = in.readNBytes(MAX_BODY_BYTES + 1);
            if (body.length > MAX_BODY_BYTES) {
                throw ApiError.of(413, "too_large", "Body terlalu besar");
            }
            return body;
        }
    }

    private static JsonObject readJson(HttpExchange ex) throws IOException, ApiError {
        String text = new String(readBody(ex), StandardCharsets.UTF_8);
        if (text.isBlank()) return new JsonObject();
        try {
            JsonElement el = JsonParser.parseString(text);
            if (!el.isJsonObject()) throw ApiError.of(400, "bad_request", "Body harus objek JSON");
            return el.getAsJsonObject();
        } catch (JsonParseException | IllegalStateException e) {
            throw ApiError.of(400, "bad_request", "JSON tidak valid");
        }
    }

    private static String requiredString(JsonObject json, String field) throws ApiError {
        String value = optionalString(json, field);
        if (value == null || value.isBlank()) {
            throw ApiError.of(400, "bad_request", "Field '" + field + "' wajib berupa string");
        }
        return value;
    }

    private static String optionalString(JsonObject json, String field) throws ApiError {
        JsonElement el = json.get(field);
        if (el == null || el.isJsonNull()) return null;
        if (!el.isJsonPrimitive() || !el.getAsJsonPrimitive().isString()) {
            throw ApiError.of(400, "bad_request", "Field '" + field + "' harus string");
        }
        return el.getAsString();
    }

    private static int intField(JsonObject json, String field) throws ApiError {
        JsonElement el = json.get(field);
        try {
            if (el != null && el.isJsonPrimitive() && el.getAsJsonPrimitive().isNumber()) {
                double d = el.getAsDouble();
                if (d == Math.rint(d) && Math.abs(d) < 1_000_000) return (int) d;
            }
        } catch (NumberFormatException ignored) {
            // jatuh ke error di bawah
        }
        throw ApiError.of(400, "bad_request", "Field '" + field + "' wajib bilangan bulat");
    }

    /** Body config: nilai string, angka, atau boolean -> string. */
    private static Map<String, String> stringMap(JsonObject json) throws ApiError {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : json.entrySet()) {
            JsonElement v = e.getValue();
            if (!v.isJsonPrimitive()) throw ApiError.of(400, "bad_request", "Nilai '" + e.getKey() + "' tidak valid");
            out.put(e.getKey(), v.getAsString());
        }
        return out;
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

    /** Error API dengan status HTTP dan body eksplisit. */
    static final class ApiError extends Exception {
        final int status;
        private final Map<String, Object> body;

        ApiError(int status, Map<String, Object> body) {
            super(String.valueOf(body.get("message")));
            this.status = status;
            this.body = body;
        }

        static ApiError of(int status, String code, String message) {
            return new ApiError(status, Map.of("error", code, "message", message));
        }

        Map<String, Object> body() {
            return body;
        }
    }
}
