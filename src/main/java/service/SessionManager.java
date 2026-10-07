package service;

import config.AppConfig;
import config.ConfigStore;
import exception.BoothException;
import exception.BoothException.Kind;
import export.LocalExportStrategy;
import factory.TemplateFactory;
import filter.FilterStrategy;
import model.StripTemplate;
import repository.SessionRepository;
import template.BrandedStripTemplate;
import template.StripLayout;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;

/**
 * Sesi booth yang dikendalikan UI (Fase 2): frame JPEG dikirim UI, dikomposisi
 * menjadi strip bermerek, lalu diekspor. Setiap sesi selalu tersimpan di disk
 * lewat SessionRepository (frame_N.jpg, strip.png, meta.properties).
 */
public class SessionManager {

    static final int MAX_FRAME_BYTES = 20 * 1024 * 1024;
    private static final DateTimeFormatter CAPTION_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    public enum Status { ACTIVE, COMPOSED, EXPORTED, ABANDONED }

    /** Hasil compose: versi strip naik setiap compose (untuk cache-busting di UI). */
    public record ComposeResult(String sessionId, int version) { }

    private static final class Session {
        final String id;
        final StripLayout layout;
        final byte[][] frames;
        int stripVersion;
        Status status = Status.ACTIVE;

        Session(String id, StripLayout layout) {
            this.id = id;
            this.layout = layout;
            this.frames = new byte[layout.photos()][];
        }
    }

    private final SessionRepository repository;
    private final ConfigStore configStore;
    private final TemplateFactory templateFactory = new TemplateFactory();
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public SessionManager(SessionRepository repository, ConfigStore configStore) {
        if (repository == null || configStore == null) {
            throw new IllegalArgumentException("repository dan config wajib diisi");
        }
        this.repository = repository;
        this.configStore = configStore;
    }

    /** Konfigurasi terkini (bisa berubah lewat Mode Operator tanpa restart). */
    public AppConfig config() {
        return configStore.current();
    }

    /** Caption footer strip: "{event.name} · dd.MM.yyyy". */
    public String caption() {
        AppConfig config = config();
        String date = config.eventDate().format(CAPTION_DATE);
        String name = config.eventName();
        return name.isEmpty() ? date : name + " · " + date;
    }

    public String createSession(String layoutId) throws BoothException, IOException {
        StripLayout layout = StripLayout.byId(layoutId)
                .orElseThrow(() -> new BoothException(Kind.BAD_REQUEST, "Layout tidak dikenal: " + layoutId));
        String id = repository.createSession(Map.of(
                "layout", layout.id(),
                "photos", String.valueOf(layout.photos()),
                "status", status(Status.ACTIVE)));
        sessions.put(id, new Session(id, layout));
        return id;
    }

    public void putFrame(String sessionId, int index, byte[] jpeg) throws BoothException, IOException {
        Session s = require(sessionId);
        synchronized (s) {
            checkIndex(s, index);
            if (jpeg == null || jpeg.length < 4) {
                throw new BoothException(Kind.BAD_REQUEST, "Body JPEG kosong");
            }
            if (jpeg.length > MAX_FRAME_BYTES) {
                throw new BoothException(Kind.BAD_REQUEST, "Frame terlalu besar");
            }
            if ((jpeg[0] & 0xFF) != 0xFF || (jpeg[1] & 0xFF) != 0xD8 || decode(jpeg) == null) {
                throw new BoothException(Kind.BAD_REQUEST, "Body bukan JPEG yang valid");
            }
            repository.writeFile(s.id, frameName(index), jpeg);
            s.frames[index - 1] = jpeg;
            if (s.status == Status.COMPOSED || s.status == Status.EXPORTED) {
                s.status = Status.ACTIVE;
            }
            repository.updateMeta(s.id, Map.of("status", status(s.status), "frames", String.valueOf(frameCount(s))));
        }
    }

    public byte[] getFrame(String sessionId, int index) throws BoothException {
        Session s = require(sessionId);
        synchronized (s) {
            checkIndex(s, index);
            byte[] frame = s.frames[index - 1];
            if (frame == null) {
                throw new BoothException(Kind.NOT_FOUND, "Frame " + index + " belum ada");
            }
            return frame;
        }
    }

    public ComposeResult compose(String sessionId, String filterId) throws BoothException, IOException {
        Session s = require(sessionId);
        BoothFilter filter = BoothFilter.byId(filterId)
                .orElseThrow(() -> new BoothException(Kind.BAD_REQUEST, "Filter tidak dikenal: " + filterId));
        synchronized (s) {
            if (frameCount(s) < s.layout.photos()) {
                throw new BoothException(Kind.CONFLICT,
                        "Frame belum lengkap: " + frameCount(s) + " dari " + s.layout.photos());
            }
            FilterStrategy strategy = filter.strategy();
            ArrayList<BufferedImage> cells = new ArrayList<>();
            for (byte[] jpeg : s.frames) {
                BufferedImage decoded = decode(jpeg);
                if (decoded == null) {
                    throw new IOException("Frame tersimpan tidak bisa dibaca");
                }
                // Scale dulu ke ukuran sel agar filter berjalan pada gambar kecil
                cells.add(strategy.applyFilter(BrandedStripTemplate.fitCell(decoded)));
            }
            StripTemplate template = templateFactory.createTemplate(s.layout.id(), caption());
            BufferedImage strip = template.applyTemplate(cells);

            ByteArrayOutputStream png = new ByteArrayOutputStream();
            ImageIO.write(strip, "png", png);
            repository.writeFile(s.id, "strip.png", png.toByteArray());
            s.stripVersion++;
            s.status = Status.COMPOSED;
            repository.updateMeta(s.id, Map.of("status", status(s.status), "filter", filter.id()));
            return new ComposeResult(s.id, s.stripVersion);
        }
    }

    public Path stripPath(String sessionId) throws BoothException {
        Session s = require(sessionId);
        synchronized (s) {
            Path strip = repository.sessionDir(s.id).resolve("strip.png");
            if (s.stripVersion == 0 || !Files.isRegularFile(strip)) {
                throw new BoothException(Kind.NOT_FOUND, "Strip belum dibuat");
            }
            return strip;
        }
    }

    /** Export lokal: menyalin strip ke {@code <output>/exports/<sessionId>.png} lewat LocalExportStrategy. */
    public Path exportLocal(String sessionId) throws BoothException, IOException {
        Session s = require(sessionId);
        synchronized (s) {
            Path strip;
            try {
                strip = stripPath(sessionId);
            } catch (BoothException e) {
                throw new BoothException(Kind.CONFLICT, "Strip belum dibuat, panggil compose dulu");
            }
            BufferedImage image = ImageIO.read(strip.toFile());
            LocalExportStrategy local = new LocalExportStrategy(config().exportsDir().resolve(s.id + ".png").toFile());
            if (!local.export(image, null)) {
                throw new IOException("Gagal menyimpan ke " + local.getTarget());
            }
            Path exported = local.getTarget().toPath().toAbsolutePath();
            s.status = Status.EXPORTED;
            repository.updateMeta(s.id, Map.of("status", status(s.status), "export", exported.toString()));
            return exported;
        }
    }

    public void abandon(String sessionId) throws BoothException, IOException {
        Session s = require(sessionId);
        synchronized (s) {
            s.status = Status.ABANDONED;
            repository.updateMeta(s.id, Map.of("status", status(s.status)));
            sessions.remove(s.id);
        }
    }

    private Session require(String sessionId) throws BoothException {
        Session s = SessionRepository.isValidSessionId(sessionId) ? sessions.get(sessionId) : null;
        if (s == null) {
            throw new BoothException(Kind.NOT_FOUND, "Sesi tidak ditemukan: " + sessionId);
        }
        return s;
    }

    private static void checkIndex(Session s, int index) throws BoothException {
        if (index < 1 || index > s.layout.photos()) {
            throw new BoothException(Kind.BAD_REQUEST,
                    "Index frame harus 1.." + s.layout.photos() + ", diterima " + index);
        }
    }

    private static int frameCount(Session s) {
        int n = 0;
        for (byte[] f : s.frames) if (f != null) n++;
        return n;
    }

    private static String frameName(int index) {
        return "frame_" + index + ".jpg";
    }

    private static String status(Status status) {
        return status.name().toLowerCase();
    }

    private static BufferedImage decode(byte[] bytes) {
        try {
            return ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            return null;
        }
    }
}
