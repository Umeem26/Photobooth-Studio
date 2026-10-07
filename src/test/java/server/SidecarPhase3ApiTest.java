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
import com.google.zxing.BinaryBitmap;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import config.AppConfig;
import config.ConfigStore;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Properties;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;

class SidecarPhase3ApiTest {

    private static final String TOKEN = "test-token-0123456789abcdef";

    @TempDir
    Path tmp;

    private SidecarApp app;
    private FakePrinters printers;
    private HttpClient http;
    private String base;

    private void start(boolean paymentOn) throws Exception {
        Properties p = new Properties();
        p.setProperty(AppConfig.OUTPUT_DIR_KEY, tmp.resolve("out").toString());
        p.setProperty(AppConfig.EVENT_DATE_KEY, "2026-10-12");
        p.setProperty(AppConfig.PAYMENT_ENABLED_KEY, String.valueOf(paymentOn));
        p.setProperty(AppConfig.SHARE_BIND_KEY, "127.0.0.1");
        p.setProperty(AppConfig.SHARE_HOST_KEY, "127.0.0.1");
        p.setProperty(AppConfig.SHARE_PORT_KEY, "0");
        printers = new FakePrinters("Booth Printer", "Spare");
        app = SidecarApp.start(ConfigStore.of(p, tmp.resolve("cfg")), printers, TOKEN, 0);
        http = HttpClient.newHttpClient();
        base = "http://127.0.0.1:" + app.port();
    }

    @BeforeEach
    void startDefault() throws Exception {
        start(false);
    }

    @AfterEach
    void stop() {
        app.close();
    }

    private HttpResponse<byte[]> send(String method, String path, byte[] body, String admin) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(path.startsWith("http") ? path : base + path))
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body))
                .header(SidecarServer.TOKEN_HEADER, TOKEN);
        if (admin != null) b.header(SidecarServer.ADMIN_HEADER, admin);
        return http.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private HttpResponse<byte[]> send(String method, String path, String json) throws Exception {
        return send(method, path, json == null ? null : json.getBytes(), null);
    }

    private HttpResponse<byte[]> admin(String method, String path, String json, String token) throws Exception {
        return send(method, path, json == null ? null : json.getBytes(), token);
    }

    private static JsonObject obj(HttpResponse<byte[]> r) {
        return JsonParser.parseString(new String(r.body())).getAsJsonObject();
    }

    private static byte[] jpeg() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(640, 480, BufferedImage.TYPE_INT_RGB), "jpg", out);
        return out.toByteArray();
    }

    private static String decodeQr(String base64) throws Exception {
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(base64)));
        return new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(img)))).getText();
    }

    private String composedSession() throws Exception {
        String id = obj(send("POST", "/api/sessions", "{\"layout\":\"vertical-3\"}")).get("sessionId").getAsString();
        for (int i = 1; i <= 3; i++) assertEquals(200, send("PUT", "/api/sessions/" + id + "/frames/" + i, jpeg(), null).statusCode());
        assertEquals(200, send("POST", "/api/sessions/" + id + "/compose", "{\"filter\":\"original\"}").statusCode());
        return id;
    }

    private String loginAsOperator() throws Exception {
        HttpResponse<byte[]> r = send("POST", "/api/admin/pin", "{\"pin\":\"482915\"}");
        assertEquals(201, r.statusCode(), new String(r.body()));
        return obj(r).get("token").getAsString();
    }

    // ------------------------------------------------------------------ payment

    @Test
    void configExposesPhaseThreeSettings() throws Exception {
        JsonObject c = obj(send("GET", "/api/config", (String) null));
        assertFalse(c.getAsJsonObject("payment").get("enabled").getAsBoolean());
        assertEquals(25000, c.getAsJsonObject("payment").get("price").getAsInt());
        assertTrue(c.getAsJsonObject("share").get("enabled").getAsBoolean());
        assertEquals(2, c.getAsJsonObject("print").get("maxCopies").getAsInt());
        assertEquals(3, c.get("countdownSeconds").getAsInt());
        assertEquals(1, c.get("pauseSeconds").getAsInt());
    }

    @Test
    void paymentOnBlocksCaptureWith402UntilSimulated() throws Exception {
        app.close();
        start(true);
        String id = obj(send("POST", "/api/sessions", "{\"layout\":\"vertical-4\"}")).get("sessionId").getAsString();
        HttpResponse<byte[]> blocked = send("PUT", "/api/sessions/" + id + "/frames/1", jpeg(), null);
        assertEquals(402, blocked.statusCode());
        assertEquals("payment_required", obj(blocked).get("error").getAsString());

        JsonObject start = obj(send("POST", "/api/sessions/" + id + "/payment", "{}"));
        assertEquals("pending", start.get("status").getAsString());
        assertEquals(25000, start.get("amount").getAsInt());
        assertEquals("DEMO-PAYMENT-NOT-REAL", decodeQr(start.get("qrPng").getAsString()));
        assertEquals("pending", obj(send("GET", "/api/sessions/" + id + "/payment", (String) null)).get("status").getAsString());

        assertEquals("paid", obj(send("POST", "/api/sessions/" + id + "/payment/simulate", "{}")).get("status").getAsString());
        assertEquals(200, send("PUT", "/api/sessions/" + id + "/frames/1", jpeg(), null).statusCode());

        String next = obj(send("POST", "/api/sessions", "{\"layout\":\"vertical-4\",\"continueFrom\":\"" + id + "\"}"))
                .get("sessionId").getAsString();
        assertEquals("paid", obj(send("GET", "/api/sessions/" + next + "/payment", (String) null)).get("status").getAsString());
    }

    @Test
    void paymentOffReturnsConflictOnPaymentEndpoints() throws Exception {
        String id = obj(send("POST", "/api/sessions", "{\"layout\":\"vertical-4\"}")).get("sessionId").getAsString();
        assertEquals(409, send("POST", "/api/sessions/" + id + "/payment", "{}").statusCode());
        JsonObject status = obj(send("GET", "/api/sessions/" + id + "/payment", (String) null));
        assertEquals("paid", status.get("status").getAsString());
        assertFalse(status.get("required").getAsBoolean());
    }

    // ------------------------------------------------------------------ share

    @Test
    void shareReturnsWorkingLocalUrlAndQr() throws Exception {
        String id = obj(send("POST", "/api/sessions", "{\"layout\":\"vertical-3\"}")).get("sessionId").getAsString();
        assertEquals(409, send("POST", "/api/sessions/" + id + "/share", "{}").statusCode(), "belum compose");
        id = composedSession();

        JsonObject share = obj(send("POST", "/api/sessions/" + id + "/share", "{}"));
        String url = share.get("url").getAsString();
        assertTrue(url.matches("http://127\\.0\\.0\\.1:\\d+/s/[A-Za-z0-9_-]{22}"), url);
        assertEquals(url, decodeQr(share.get("qrPng").getAsString()));

        HttpResponse<String> page = http.send(HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, page.statusCode());
        assertTrue(page.body().contains("Download strip"));
        HttpResponse<byte[]> strip = http.send(HttpRequest.newBuilder(URI.create(url + "/strip.png")).build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertArrayEquals(Files.readAllBytes(tmp.resolve("out/sessions").resolve(id).resolve("strip.png")), strip.body());
    }

    // ------------------------------------------------------------------ print

    @Test
    void printQueuesAsyncPollsAndStopsAtLimit() throws Exception {
        JsonArray list = JsonParser.parseString(new String(send("GET", "/api/printers", (String) null).body())).getAsJsonArray();
        assertEquals("Booth Printer", list.get(0).getAsJsonObject().get("name").getAsString());
        assertTrue(list.get(0).getAsJsonObject().get("default").getAsBoolean());

        String id = composedSession();
        for (int i = 1; i <= 2; i++) {
            HttpResponse<byte[]> r = send("POST", "/api/sessions/" + id + "/print", "{\"copies\":1}");
            assertEquals(202, r.statusCode());
            assertTrue(obj(r).get("queued").getAsBoolean());
            String status = "";
            for (int t = 0; t < 200 && !status.equals("done"); t++) {
                status = obj(send("GET", "/api/sessions/" + id + "/print", (String) null)).get("status").getAsString();
                Thread.sleep(20);
            }
            assertEquals("done", status);
        }
        HttpResponse<byte[]> limit = send("POST", "/api/sessions/" + id + "/print", "{\"copies\":1}");
        assertEquals(409, limit.statusCode());
        assertEquals(2, printers.printed.size());
        assertEquals(400, send("POST", "/api/sessions/" + id + "/print", "{\"copies\":\"dua\"}").statusCode());
    }

    // ------------------------------------------------------------------ admin

    @Test
    void pinSetupLoginAndLockout() throws Exception {
        assertFalse(obj(send("GET", "/api/admin/pin", (String) null)).get("set").getAsBoolean());
        assertEquals(409, send("POST", "/api/admin/login", "{\"pin\":\"0000\"}").statusCode(), "belum ada PIN bawaan");
        assertEquals(400, send("POST", "/api/admin/pin", "{\"pin\":\"12\"}").statusCode());
        String token = loginAsOperator();
        assertEquals(409, send("POST", "/api/admin/pin", "{\"pin\":\"111111\"}").statusCode());
        assertTrue(obj(send("GET", "/api/admin/pin", (String) null)).get("set").getAsBoolean());
        assertFalse(Files.readString(tmp.resolve("cfg/operator-pin.properties")).contains("482915"));

        for (int left = 4; left >= 1; left--) {
            HttpResponse<byte[]> wrong = send("POST", "/api/admin/login", "{\"pin\":\"000000\"}");
            assertEquals(403, wrong.statusCode());
            assertEquals(left, obj(wrong).get("attemptsLeft").getAsInt());
        }
        HttpResponse<byte[]> locked = send("POST", "/api/admin/login", "{\"pin\":\"000000\"}");
        assertEquals(423, locked.statusCode());
        assertEquals(30, obj(locked).get("retryAfterSeconds").getAsInt());
        assertEquals(423, send("POST", "/api/admin/login", "{\"pin\":\"482915\"}").statusCode());

        assertEquals(200, admin("GET", "/api/admin/config", null, token).statusCode(), "token lama tetap berlaku");
        assertEquals(200, admin("POST", "/api/admin/logout", "{}", token).statusCode());
        assertEquals(403, admin("GET", "/api/admin/config", null, token).statusCode());
    }

    @Test
    void adminEndpointsRequireAdminToken() throws Exception {
        for (String[] r : new String[][] {{"GET", "/api/admin/config"}, {"PUT", "/api/admin/config"},
                {"GET", "/api/admin/sessions"}, {"POST", "/api/admin/export-all"}, {"POST", "/api/admin/purge"},
                {"GET", "/api/admin/status"}, {"DELETE", "/api/admin/sessions/20261012_100000_000"},
                {"POST", "/api/admin/print-test"}, {"POST", "/api/admin/share-test"}}) {
            HttpResponse<byte[]> res = admin(r[0], r[1], "{}", "token-palsu");
            assertEquals(403, res.statusCode(), r[1]);
            assertEquals("admin_unauthorized", obj(res).get("error").getAsString());
        }
        // tanpa token booth tetap 401
        HttpResponse<byte[]> noBooth = http.send(HttpRequest.newBuilder(URI.create(base + "/api/admin/pin")).build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(401, noBooth.statusCode());
    }

    @Test
    void configUpdateValidatesAndAppliesWithoutRestart() throws Exception {
        String token = loginAsOperator();
        JsonObject cfg = obj(admin("GET", "/api/admin/config", null, token));
        assertEquals("Sample event", cfg.get("event.name").getAsString());
        assertFalse(cfg.has("output.dir"), "tanpa path sensitif");

        HttpResponse<byte[]> bad = admin("PUT", "/api/admin/config", "{\"countdown.seconds\":9}", token);
        assertEquals(400, bad.statusCode());
        assertEquals(400, admin("PUT", "/api/admin/config", "{\"output.dir\":\"C:/\"}", token).statusCode());

        HttpResponse<byte[]> ok = admin("PUT", "/api/admin/config",
                "{\"event.name\":\"Rina & Bayu\",\"payment.enabled\":true,\"payment.price\":30000,\"print.maxCopies\":3}", token);
        assertEquals(200, ok.statusCode(), new String(ok.body()));
        JsonObject pub = obj(send("GET", "/api/config", (String) null));
        assertEquals("Rina & Bayu", pub.get("event.name").getAsString());
        assertTrue(pub.getAsJsonObject("payment").get("enabled").getAsBoolean());
        assertEquals(30000, pub.getAsJsonObject("payment").get("price").getAsInt());
        assertEquals(3, pub.getAsJsonObject("print").get("maxCopies").getAsInt());
        assertTrue(Files.readString(tmp.resolve("cfg/config.properties")).contains("Rina"));
    }

    @Test
    void shareSettingsRestartShareServer() throws Exception {
        String token = loginAsOperator();
        int before = obj(admin("GET", "/api/admin/status", null, token)).get("sharePort").getAsInt();
        assertEquals(200, admin("PUT", "/api/admin/config", "{\"share.enabled\":false}", token).statusCode());
        JsonObject off = obj(admin("GET", "/api/admin/status", null, token));
        assertFalse(off.get("shareRunning").getAsBoolean());
        assertEquals(409, admin("POST", "/api/admin/share-test", "{}", token).statusCode());

        assertEquals(200, admin("PUT", "/api/admin/config", "{\"share.enabled\":true}", token).statusCode());
        JsonObject on = obj(admin("GET", "/api/admin/status", null, token));
        assertTrue(on.get("shareRunning").getAsBoolean());
        assertTrue(before > 0);
        JsonObject test = obj(admin("POST", "/api/admin/share-test", "{}", token));
        HttpResponse<String> page = http.send(HttpRequest.newBuilder(URI.create(test.get("url").getAsString())).build(),
                HttpResponse.BodyHandlers.ofString());
        assertTrue(page.body().contains("works."));
    }

    @Test
    void galleryListThumbDeleteExportAndPurge() throws Exception {
        String token = loginAsOperator();
        String a = composedSession();
        String b = composedSession();

        JsonArray sessions = JsonParser.parseString(new String(admin("GET", "/api/admin/sessions", null, token).body()))
                .getAsJsonArray();
        assertEquals(2, sessions.size());
        JsonObject first = sessions.get(0).getAsJsonObject();
        assertEquals(b, first.get("id").getAsString());
        assertEquals("composed", first.get("status").getAsString());
        assertTrue(first.get("hasStrip").getAsBoolean());

        HttpResponse<byte[]> thumb = admin("GET", "/api/admin/sessions/" + a + "/thumb.jpg", null, token);
        assertEquals(200, thumb.statusCode());
        assertNotNull(ImageIO.read(new ByteArrayInputStream(thumb.body())));

        JsonObject zip = obj(admin("POST", "/api/admin/export-all", "{}", token));
        try (ZipFile z = new ZipFile(zip.get("path").getAsString())) {
            assertNotNull(z.getEntry("sessions/" + a + "/strip.png"));
        }

        assertEquals(200, admin("DELETE", "/api/admin/sessions/" + b, null, token).statusCode());
        assertEquals(404, admin("DELETE", "/api/admin/sessions/" + b, null, token).statusCode());
        assertEquals(404, admin("DELETE", "/api/admin/sessions/..%2F..%2Fcfg", null, token).statusCode());
        assertEquals(404, send("GET", "/api/sessions/" + b + "/strip.png", (String) null).statusCode());

        assertEquals(0, obj(admin("POST", "/api/admin/purge", "{\"olderThanDays\":7}", token)).get("deleted").getAsInt());
        assertEquals(400, admin("POST", "/api/admin/purge", "{\"olderThanDays\":-1}", token).statusCode());
        assertEquals(1, obj(admin("POST", "/api/admin/purge", "{\"olderThanDays\":0}", token)).get("deleted").getAsInt());
        assertEquals(0, JsonParser.parseString(new String(admin("GET", "/api/admin/sessions", null, token).body()))
                .getAsJsonArray().size());
    }

    @Test
    void statusAndPrintTestPage() throws Exception {
        String token = loginAsOperator();
        JsonObject s = obj(admin("GET", "/api/admin/status", null, token));
        assertFalse(s.get("version").getAsString().isBlank());
        assertEquals(2, s.getAsJsonArray("printers").size());
        assertTrue(s.get("diskFreeBytes").getAsLong() > 0);
        assertTrue(s.get("shareUrl").getAsString().startsWith("http://127.0.0.1:"));

        assertEquals(202, admin("POST", "/api/admin/print-test", "{}", token).statusCode());
        String status = "";
        for (int t = 0; t < 200 && !status.equals("done"); t++) {
            JsonElement el = obj(admin("GET", "/api/admin/print-test", null, token)).get("status");
            status = el.getAsString();
            Thread.sleep(20);
        }
        assertEquals("done", status);
        assertEquals(1, printers.printed.size());
    }
}
