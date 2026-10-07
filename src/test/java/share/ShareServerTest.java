package share;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import server.MutableClock;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

class ShareServerTest {

    @TempDir
    Path tmp;

    private MutableClock clock;
    private ShareRegistry registry;
    private ShareServer server;
    private Path sessions;
    private static final String SESSION = "20261012_100000_000";

    @BeforeEach
    void start() throws Exception {
        clock = new MutableClock(Instant.parse("2026-10-12T10:00:00Z"));
        registry = new ShareRegistry(clock);
        sessions = tmp.resolve("sessions");
        Path dir = Files.createDirectories(sessions.resolve(SESSION));
        Files.write(dir.resolve("strip.png"), new byte[] {1, 2, 3});
        Files.write(dir.resolve("frame_1.jpg"), new byte[] {4, 5});
        Files.write(dir.resolve("frame_2.jpg"), new byte[] {6});
        Files.writeString(dir.resolve("meta.properties"), "rahasia=meta");
        Files.writeString(tmp.resolve("config.properties"), "rahasia=config");
        server = new ShareServer(registry, sessions, new RateLimiter(1000, clock), "127.0.0.1", 0);
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop();
    }

    /** Request HTTP mentah supaya path tidak dinormalisasi klien. */
    private String[] raw(String method, String path) throws Exception {
        try (Socket s = new Socket("127.0.0.1", server.port())) {
            OutputStream out = s.getOutputStream();
            out.write((method + " " + path + " HTTP/1.1\r\nHost: booth\r\nConnection: close\r\n\r\n")
                    .getBytes(StandardCharsets.ISO_8859_1));
            out.flush();
            InputStream in = s.getInputStream();
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            in.transferTo(buf);
            String text = buf.toString(StandardCharsets.ISO_8859_1);
            String status = text.substring(9, 12);
            String body = text.substring(text.indexOf("\r\n\r\n") + 4);
            return new String[] {status, body, text};
        }
    }

    @Test
    void validTokenServesPageStripAndPhotos() throws Exception {
        String token = registry.linkFor(SESSION, Duration.ofHours(6)).token();
        String[] page = raw("GET", "/s/" + token);
        assertEquals("200", page[0]);
        assertTrue(page[1].contains("Download strip"));
        assertTrue(page[1].contains("/s/" + token + "/photo/2.jpg"));
        assertFalse(page[1].contains("photo/3.jpg"));
        assertTrue(page[2].toLowerCase().contains("content-security-policy: default-src 'none'"));
        assertEquals("200", raw("GET", "/s/" + token + "/strip.png")[0]);
        assertEquals("200", raw("GET", "/s/" + token + "/photo/1.jpg")[0]);
        assertEquals("404", raw("GET", "/s/" + token + "/photo/3.jpg")[0]);
    }

    @Test
    void reusesTokenPerSessionAndTokenIsRandomBase64Url() {
        String a = registry.linkFor(SESSION, Duration.ofHours(6)).token();
        assertEquals(a, registry.linkFor(SESSION, Duration.ofHours(6)).token());
        assertTrue(ShareRegistry.TOKEN.matcher(a).matches());
        assertNotEquals(a, registry.linkFor("20261012_100000_001", Duration.ofHours(6)).token());
    }

    @Test
    void unknownTokenAndOtherPathsAre404() throws Exception {
        for (String p : new String[] {"/", "/s/", "/s", "/api/config", "/s/AAAAAAAAAAAAAAAAAAAAAA",
                "/sessions/" + SESSION + "/strip.png", "/favicon.ico"}) {
            assertEquals("404", raw("GET", p)[0], p);
        }
        String token = registry.linkFor(SESSION, Duration.ofHours(6)).token();
        assertEquals("404", raw("POST", "/s/" + token)[0], "hanya GET");
    }

    @Test
    void pathTraversalNeverLeavesTheSessionFolder() throws Exception {
        String token = registry.linkFor(SESSION, Duration.ofHours(6)).token();
        String[] attacks = {
            "/s/" + token + "/../meta.properties",
            "/s/" + token + "/meta.properties",
            "/s/" + token + "/photo/../meta.properties",
            "/s/" + token + "/photo/..%2Fmeta.properties",
            "/s/" + token + "/%2e%2e/%2e%2e/config.properties",
            "/s/" + token + "/photo/1.jpg/../../meta.properties",
            "/s/" + token + "/photo/%2e%2e%2f%2e%2e%2fconfig.properties",
            "/s/" + token + "/photo/1.jpg%00.png",
            "/s/../config.properties",
            "/s/..%2F..%2Fconfig.properties",
            "/s/" + token + "/strip.png/..",
            "/s/" + token + "\\..\\meta.properties",
        };
        for (String a : attacks) {
            String[] r = raw("GET", a);
            assertNotEquals("200", r[0], a);
            assertFalse(r[1].contains("rahasia"), a);
        }
    }

    @Test
    void testLinkShowsTestPageButNoFiles() throws Exception {
        String token = registry.linkFor(null, Duration.ofHours(1)).token();
        String[] page = raw("GET", "/s/" + token);
        assertEquals("200", page[0]);
        assertTrue(page[1].contains("Sharing <em>works.</em>"));
        assertEquals("404", raw("GET", "/s/" + token + "/strip.png")[0]);
    }

    @Test
    void expiredTokenIs404() throws Exception {
        String token = registry.linkFor(SESSION, Duration.ofHours(6)).token();
        clock.advance(Duration.ofHours(5).plusMinutes(59));
        assertEquals("200", raw("GET", "/s/" + token)[0]);
        clock.advance(Duration.ofMinutes(2));
        assertEquals("404", raw("GET", "/s/" + token)[0]);
        assertEquals("404", raw("GET", "/s/" + token + "/strip.png")[0]);
    }

    @Test
    void revokedSessionIs404() throws Exception {
        String token = registry.linkFor(SESSION, Duration.ofHours(6)).token();
        registry.revokeSession(SESSION);
        assertEquals("404", raw("GET", "/s/" + token)[0]);
    }

    @Test
    void rateLimitPerIp() throws Exception {
        server.stop();
        server = new ShareServer(registry, sessions, new RateLimiter(3, clock), "127.0.0.1", 0);
        server.start();
        String token = registry.linkFor(SESSION, Duration.ofHours(6)).token();
        for (int i = 0; i < 3; i++) assertEquals("200", raw("GET", "/s/" + token)[0]);
        assertEquals("429", raw("GET", "/s/" + token)[0]);
        clock.advance(Duration.ofSeconds(61));
        assertEquals("200", raw("GET", "/s/" + token)[0]);
    }

    @Test
    void privateAddressDetection() throws Exception {
        assertTrue(NetworkAddress.isPrivate((Inet4Address) InetAddress.getByName("192.168.4.1")));
        assertTrue(NetworkAddress.isPrivate((Inet4Address) InetAddress.getByName("10.1.2.3")));
        assertTrue(NetworkAddress.isPrivate((Inet4Address) InetAddress.getByName("172.20.0.5")));
        assertFalse(NetworkAddress.isPrivate((Inet4Address) InetAddress.getByName("172.32.0.5")));
        assertFalse(NetworkAddress.isPrivate((Inet4Address) InetAddress.getByName("8.8.8.8")));
        assertFalse(NetworkAddress.isPrivate((Inet4Address) InetAddress.getByName("127.0.0.1")));
        NetworkAddress.detectPrivateIPv4().ifPresent(ip -> assertFalse(ip.startsWith("127.")));
    }
}
