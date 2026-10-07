package server;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import config.AppConfig;
import service.PhotoboothService;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import javax.imageio.ImageIO;

class SidecarServerTest {

    private static final String TOKEN = "test-token-0123456789abcdef";

    @TempDir
    Path tmp;

    private PhotoboothService service;
    private SidecarServer server;
    private HttpClient http;
    private String base;

    @BeforeEach
    void start() throws Exception {
        Properties p = new Properties();
        p.setProperty(AppConfig.OUTPUT_DIR_KEY, tmp.toString());
        p.setProperty(AppConfig.EVENT_NAME_KEY, "Sample event");
        p.setProperty(AppConfig.EVENT_DATE_KEY, "2026-10-12");
        p.setProperty(AppConfig.MAX_RETAKES_KEY, "2");
        service = PhotoboothService.forSidecar(AppConfig.fromProperties(p));
        server = new SidecarServer(service, TOKEN, 0);
        server.start();
        http = HttpClient.newHttpClient();
        base = "http://127.0.0.1:" + server.port();
    }

    @AfterEach
    void stop() {
        server.stop();
        service.shutdown();
    }

    // ------------------------------------------------------------- helpers

    private HttpResponse<byte[]> send(String method, String path, byte[] body, boolean withToken) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path))
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofByteArray(body));
        if (withToken) b.header(SidecarServer.TOKEN_HEADER, TOKEN);
        return http.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private HttpResponse<byte[]> send(String method, String path, String json) throws Exception {
        return send(method, path, json == null ? null : json.getBytes(), true);
    }

    private static JsonElement json(HttpResponse<byte[]> r) {
        return JsonParser.parseString(new String(r.body()));
    }

    private static byte[] jpeg(Color color, int w, int h) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, w, h);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", out);
        return out.toByteArray();
    }

    private String createSession(String layout) throws Exception {
        HttpResponse<byte[]> r = send("POST", "/api/sessions", "{\"layout\":\"" + layout + "\"}");
        assertEquals(201, r.statusCode());
        return json(r).getAsJsonObject().get("sessionId").getAsString();
    }

    private void putAllFrames(String id, int n) throws Exception {
        for (int i = 1; i <= n; i++) {
            assertEquals(200, send("PUT", "/api/sessions/" + id + "/frames/" + i, jpeg(Color.GRAY, 1440, 1080), true)
                    .statusCode());
        }
    }

    private static void assertError(HttpResponse<byte[]> r, int status, String code) {
        assertEquals(status, r.statusCode(), new String(r.body()));
        JsonObject body = json(r).getAsJsonObject();
        assertEquals(code, body.get("error").getAsString());
        assertTrue(body.has("message"));
    }

    // ------------------------------------------------------------- endpoints

    @Test
    void healthReturnsOkAndVersion() throws Exception {
        HttpResponse<byte[]> r = send("GET", "/health", (String) null);
        assertEquals(200, r.statusCode());
        JsonObject body = json(r).getAsJsonObject();
        assertTrue(body.get("ok").getAsBoolean());
        assertFalse(body.get("version").getAsString().isBlank());
    }

    @Test
    void rejectsMissingOrWrongToken() throws Exception {
        assertError(send("GET", "/health", null, false), 401, "unauthorized");
        HttpResponse<byte[]> wrong = http.send(HttpRequest.newBuilder(URI.create(base + "/api/layouts"))
                .header(SidecarServer.TOKEN_HEADER, "salah-salah-salah-salah").build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertError(wrong, 401, "unauthorized");
    }

    @Test
    void corsPreflightNeedsNoToken() throws Exception {
        HttpResponse<byte[]> r = send("OPTIONS", "/api/sessions", null, false);
        assertEquals(204, r.statusCode());
        assertEquals("*", r.headers().firstValue("Access-Control-Allow-Origin").orElse(""));
        assertTrue(r.headers().firstValue("Access-Control-Allow-Headers").orElse("").contains("X-Booth-Token"));
    }

    @Test
    void configExposesEventAndRetakesWithoutPaths() throws Exception {
        HttpResponse<byte[]> r = send("GET", "/api/config", (String) null);
        assertEquals(200, r.statusCode());
        JsonObject body = json(r).getAsJsonObject();
        assertEquals("Sample event", body.get("event.name").getAsString());
        assertEquals("2026-10-12", body.get("event.date").getAsString());
        assertEquals(2, body.get("maxRetakes").getAsInt());
        assertEquals(4, body.getAsJsonObject("photosPerLayout").get("vertical-4").getAsInt());
        assertFalse(new String(r.body()).contains(tmp.getFileName().toString()), "tanpa path sensitif");
    }

    @Test
    void layoutsListsThreeLayouts() throws Exception {
        JsonArray arr = json(send("GET", "/api/layouts", (String) null)).getAsJsonArray();
        assertEquals(3, arr.size());
        JsonObject first = arr.get(0).getAsJsonObject();
        assertEquals("vertical-4", first.get("id").getAsString());
        assertEquals("Vertical, 4 photos", first.get("name").getAsString());
        assertEquals(4, first.get("photos").getAsInt());
        assertEquals("vertical", first.get("orientation").getAsString());
        assertEquals("horizontal-3", arr.get(2).getAsJsonObject().get("id").getAsString());
        assertEquals("horizontal", arr.get(2).getAsJsonObject().get("orientation").getAsString());
    }

    @Test
    void filtersListsFourFilters() throws Exception {
        JsonArray arr = json(send("GET", "/api/filters", (String) null)).getAsJsonArray();
        assertEquals(4, arr.size());
        assertEquals("original", arr.get(0).getAsJsonObject().get("id").getAsString());
        assertEquals("Black & white", arr.get(1).getAsJsonObject().get("name").getAsString());
        assertEquals("warm", arr.get(3).getAsJsonObject().get("id").getAsString());
    }

    @Test
    void createSessionValidatesLayoutAndPersistsToDisk() throws Exception {
        String id = createSession("vertical-3");
        assertTrue(Files.isRegularFile(tmp.resolve("sessions").resolve(id).resolve("meta.properties")));

        assertError(send("POST", "/api/sessions", "{\"layout\":\"square-9\"}"), 400, "bad_request");
        assertError(send("POST", "/api/sessions", "{}"), 400, "bad_request");
        assertError(send("POST", "/api/sessions", "bukan json"), 400, "bad_request");
        assertError(send("GET", "/api/sessions", (String) null), 405, "method_not_allowed");
    }

    @Test
    void putAndGetFrameRoundTripsJpeg() throws Exception {
        String id = createSession("vertical-4");
        byte[] frame = jpeg(Color.RED, 1440, 1080);

        assertEquals(200, send("PUT", "/api/sessions/" + id + "/frames/2", frame, true).statusCode());
        HttpResponse<byte[]> got = send("GET", "/api/sessions/" + id + "/frames/2", (String) null);
        assertEquals(200, got.statusCode());
        assertEquals("image/jpeg", got.headers().firstValue("Content-Type").orElse(""));
        assertArrayEquals(frame, got.body());
        assertTrue(Files.isRegularFile(tmp.resolve("sessions").resolve(id).resolve("frame_2.jpg")));

        // retake = PUT ulang index yang sama
        byte[] retake = jpeg(Color.BLUE, 800, 600);
        assertEquals(200, send("PUT", "/api/sessions/" + id + "/frames/2", retake, true).statusCode());
        assertArrayEquals(retake, send("GET", "/api/sessions/" + id + "/frames/2", (String) null).body());
    }

    @Test
    void frameErrors() throws Exception {
        String id = createSession("vertical-3");
        assertError(send("GET", "/api/sessions/" + id + "/frames/1", (String) null), 404, "not_found");
        assertError(send("PUT", "/api/sessions/" + id + "/frames/4", jpeg(Color.RED, 40, 30), true),
                400, "bad_request");
        assertError(send("PUT", "/api/sessions/" + id + "/frames/0", jpeg(Color.RED, 40, 30), true),
                400, "bad_request");
        assertError(send("PUT", "/api/sessions/" + id + "/frames/1", "bukan jpeg".getBytes(), true),
                400, "bad_request");
        assertError(send("PUT", "/api/sessions/20990101_000000_000/frames/1", jpeg(Color.RED, 40, 30), true),
                404, "not_found");
        assertError(send("GET", "/api/sessions/..%2F..%2Fetc/frames/1", (String) null), 404, "not_found");
    }

    @Test
    void composeRequiresAllFramesAndValidFilter() throws Exception {
        String id = createSession("vertical-3");
        send("PUT", "/api/sessions/" + id + "/frames/1", jpeg(Color.RED, 1440, 1080), true);
        assertError(send("POST", "/api/sessions/" + id + "/compose", "{\"filter\":\"original\"}"), 409, "conflict");
        assertError(send("GET", "/api/sessions/" + id + "/strip.png", (String) null), 404, "not_found");

        putAllFrames(id, 3);
        assertError(send("POST", "/api/sessions/" + id + "/compose", "{\"filter\":\"sepia\"}"), 400, "bad_request");
    }

    @Test
    void composeProducesStripPngWithChangingUrl() throws Exception {
        String id = createSession("vertical-4");
        putAllFrames(id, 4);

        HttpResponse<byte[]> r = send("POST", "/api/sessions/" + id + "/compose", "{\"filter\":\"warm\"}");
        assertEquals(200, r.statusCode(), new String(r.body()));
        String url1 = json(r).getAsJsonObject().get("stripUrl").getAsString();
        assertTrue(url1.startsWith("/api/sessions/" + id + "/strip.png"));

        HttpResponse<byte[]> png = send("GET", url1, (String) null);
        assertEquals(200, png.statusCode());
        assertEquals("image/png", png.headers().firstValue("Content-Type").orElse(""));
        BufferedImage strip = ImageIO.read(new ByteArrayInputStream(png.body()));
        assertTrue(strip.getHeight() > strip.getWidth(), "strip vertikal");

        String url2 = json(send("POST", "/api/sessions/" + id + "/compose", "{\"filter\":\"mono\"}"))
                .getAsJsonObject().get("stripUrl").getAsString();
        assertNotEquals(url1, url2);
        Properties meta = new Properties();
        try (var in = Files.newInputStream(tmp.resolve("sessions").resolve(id).resolve("meta.properties"))) {
            meta.load(in);
        }
        assertEquals("mono", meta.getProperty("filter"));
        assertEquals("composed", meta.getProperty("status"));
    }

    @Test
    void exportCopiesStripToExportsFolder() throws Exception {
        String id = createSession("horizontal-3");
        assertError(send("POST", "/api/sessions/" + id + "/export", "{\"strategy\":\"local\"}"), 409, "conflict");
        putAllFrames(id, 3);
        send("POST", "/api/sessions/" + id + "/compose", "{\"filter\":\"original\"}");

        assertError(send("POST", "/api/sessions/" + id + "/export", "{\"strategy\":\"drive\"}"), 400, "bad_request");
        HttpResponse<byte[]> r = send("POST", "/api/sessions/" + id + "/export", "{\"strategy\":\"local\"}");
        assertEquals(200, r.statusCode(), new String(r.body()));
        Path exported = Path.of(json(r).getAsJsonObject().get("path").getAsString());
        assertEquals(tmp.resolve("exports").resolve(id + ".png").toAbsolutePath(), exported);
        assertTrue(Files.size(exported) > 0);
    }

    @Test
    void abandonMarksMetaAndClosesSession() throws Exception {
        String id = createSession("vertical-4");
        HttpResponse<byte[]> r = send("POST", "/api/sessions/" + id + "/abandon", "{}");
        assertEquals(200, r.statusCode());
        Properties meta = new Properties();
        try (var in = Files.newInputStream(tmp.resolve("sessions").resolve(id).resolve("meta.properties"))) {
            meta.load(in);
        }
        assertEquals("abandoned", meta.getProperty("status"));
        assertError(send("PUT", "/api/sessions/" + id + "/frames/1", jpeg(Color.RED, 40, 30), true), 404, "not_found");
        assertError(send("POST", "/api/sessions/" + id + "/abandon", "{}"), 404, "not_found");
    }

    @Test
    void unknownEndpointIs404AndServerBindsLoopbackOnly() throws Exception {
        assertError(send("GET", "/api/nope", (String) null), 404, "not_found");
        assertThrows(IllegalArgumentException.class, () -> new SidecarServer(service, "pendek", 0));
    }

    @Test
    void composeFourFullHdFramesIsFast() throws Exception {
        String id = createSession("vertical-4");
        for (int i = 1; i <= 4; i++) {
            send("PUT", "/api/sessions/" + id + "/frames/" + i, jpeg(Color.ORANGE, 1440, 1080), true);
        }
        send("POST", "/api/sessions/" + id + "/compose", "{\"filter\":\"vintage\"}"); // pemanasan JIT
        long start = System.nanoTime();
        assertEquals(200, send("POST", "/api/sessions/" + id + "/compose", "{\"filter\":\"vintage\"}").statusCode());
        long ms = (System.nanoTime() - start) / 1_000_000;
        assertTrue(ms < 1500, "compose " + ms + " ms melebihi anggaran 1,5 detik");
    }
}
