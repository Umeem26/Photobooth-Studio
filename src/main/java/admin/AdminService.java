package admin;

import config.AppConfig;
import config.ConfigStore;
import exception.BoothException;
import exception.BoothException.Kind;
import print.PrintManager;
import repository.SessionRepository;
import service.PhotoboothService;
import share.ShareService;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * Operasi Mode Operator di atas Facade: config, galeri sesi, export ZIP, purge, status.
 * Autentikasi (PIN, token, lockout) ada di {@link AdminAuth}.
 */
public class AdminService {

    static final int THUMB_WIDTH = 320;
    private static final DateTimeFormatter ZIP_NAME = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final PhotoboothService booth;
    private final AdminAuth auth;
    private final Clock clock;

    public AdminService(PhotoboothService booth, AdminAuth auth, Clock clock) {
        this.booth = booth;
        this.auth = auth;
        this.clock = clock;
    }

    public AdminAuth auth() {
        return auth;
    }

    private ConfigStore store() {
        return booth.getConfigStore();
    }

    private SessionRepository repo() {
        return booth.getSessionManager().repository();
    }

    // ------------------------------------------------------------------ config

    /** Nilai yang bisa diubah operator (tanpa path sensitif). */
    public Map<String, String> config() {
        AppConfig c = store().current();
        Map<String, String> out = new LinkedHashMap<>();
        for (String key : AppConfig.KEYS) {
            if (AppConfig.EDITABLE_KEYS.contains(key)) out.put(key, c.raw(key));
        }
        return out;
    }

    public Map<String, String> updateConfig(Map<String, String> changes) throws BoothException, IOException {
        try {
            store().update(changes);
        } catch (IllegalArgumentException e) {
            throw new BoothException(Kind.BAD_REQUEST, e.getMessage());
        }
        return config();
    }

    // ------------------------------------------------------------------ galeri

    public List<SessionRepository.SessionInfo> sessions() throws IOException {
        return repo().list();
    }

    public byte[] thumbnail(String id) throws BoothException, IOException {
        if (!repo().exists(id)) throw new BoothException(Kind.NOT_FOUND, "Sesi tidak ditemukan: " + id);
        Path dir = repo().sessionDir(id);
        Path src = Files.isRegularFile(dir.resolve("strip.png")) ? dir.resolve("strip.png") : dir.resolve("frame_1.jpg");
        if (!Files.isRegularFile(src)) throw new BoothException(Kind.NOT_FOUND, "Sesi belum punya foto");
        BufferedImage img = ImageIO.read(src.toFile());
        if (img == null) throw new BoothException(Kind.NOT_FOUND, "Gambar sesi tidak terbaca");
        int w = Math.min(THUMB_WIDTH, img.getWidth());
        int h = Math.max(1, img.getHeight() * w / img.getWidth());
        BufferedImage thumb = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = thumb.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(img, 0, 0, w, h, null);
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(thumb, "jpg", out);
        return out.toByteArray();
    }

    public void deleteSession(String id) throws BoothException, IOException {
        if (!SessionRepository.isValidSessionId(id) || !repo().delete(id)) {
            throw new BoothException(Kind.NOT_FOUND, "Sesi tidak ditemukan: " + id);
        }
        booth.getSessionManager().discard(id);
        booth.getShareService().revokeSession(id);
        booth.getPrintManager().forget(id);
    }

    public Path exportAll() throws IOException {
        Path zip = store().current().exportsDir()
                .resolve("vandebooth-sessions-" + LocalDateTime.now(clock).format(ZIP_NAME) + ".zip");
        return repo().exportAll(zip).toAbsolutePath();
    }

    public List<String> purge(int olderThanDays) throws BoothException, IOException {
        if (olderThanDays < 0) throw new BoothException(Kind.BAD_REQUEST, "olderThanDays tidak boleh negatif");
        List<String> deleted = repo().purgeOlderThan(olderThanDays);
        for (String id : deleted) {
            booth.getSessionManager().discard(id);
            booth.getShareService().revokeSession(id);
        }
        return deleted;
    }

    // ------------------------------------------------------------------ status dan alat uji

    public Map<String, Object> status(String version) throws IOException {
        AppConfig c = store().current();
        ShareService share = booth.getShareService();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("version", version);
        out.put("printers", booth.getPrintManager().printers());
        out.put("printMode", c.printToFile() ? "file" : "system");
        Path probe = c.outputDir();
        while (probe != null && !Files.exists(probe)) probe = probe.getParent();
        out.put("diskFreeBytes", probe == null ? -1 : Files.getFileStore(probe).getUsableSpace());
        out.put("outputDir", c.outputDir().toString());
        out.put("sessions", sessions().size());
        out.put("shareEnabled", c.shareEnabled());
        out.put("shareRunning", share.running());
        out.put("shareUrl", share.baseUrl().orElse(""));
        out.put("shareDetectedHost", share.detectedHost().orElse(""));
        out.put("sharePort", share.port().orElse(c.sharePort()));
        out.put("shareError", share.lastError() == null ? "" : share.lastError());
        return out;
    }

    public ShareService.ShareLink shareTest() throws BoothException {
        return booth.getShareService().testLink();
    }

    public PrintManager.JobStatus printTest() throws BoothException {
        return booth.getPrintManager().printTest(store().current().printQueueDir().resolveSibling("tmp"));
    }

    public PrintManager.JobStatus printTestStatus() {
        return booth.getPrintManager().status(PrintManager.TEST_KEY);
    }
}
