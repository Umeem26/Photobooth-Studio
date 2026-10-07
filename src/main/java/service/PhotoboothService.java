package service;

import config.AppConfig;
import config.ConfigStore;
import hardware.Camera;
import hardware.CameraManager; // Singleton
import factory.TemplateFactory; // Simple Factory
import export.ExportStrategy; // Strategy
import model.StripTemplate;
import repository.SessionRecord;
import repository.SessionRepository;
import exception.TemplateNotFoundException;
import exception.ExportFailedException;
import exception.CameraException;
import exception.BoothException;
import template.StripLayout;
import payment.PaymentStatus;
import print.PrintManager;
import print.PrinterResolver;
import share.ShareService;

import java.awt.image.BufferedImage;
import java.io.File;
import java.time.Clock;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Facade "otak" bisnis Photobooth: kamera, template, komposisi strip, arsip sesi, dan export.
 * Komposisi dan export berjalan di ExecutorService (tidak memblokir thread GUI)
 * dengan laporan progres lewat {@link ProgressListener}.
 */
public class PhotoboothService {

    private final Camera camera;
    private final TemplateFactory templateFactory;
    private final SessionRepository sessionRepository;
    private final ExecutorService executor;
    private final SessionManager sessionManager;
    private final ConfigStore configStore;

    // Hanya mode sidecar (Fase 3): berbagi lewat jaringan lokal dan cetak
    private ShareService shareService;
    private PrintManager printManager;

    // Daftar gambar yang ditangkap (diubah dari thread GUI)
    private final ArrayList<BufferedImage> capturedImages;

    // Daftar template yang tersedia (untuk GUI)
    private final Map<String, StripTemplate> availableTemplates;

    // Constructor default: webcam asli (Singleton, dibuka lazy) dan folder output dari config
    public PhotoboothService() {
        this(CameraManager.getInstance());
    }

    // Constructor untuk injeksi kamera (mis. kamera palsu di test)
    public PhotoboothService(Camera camera) {
        this(camera, new SessionRepository(AppConfig.get().sessionsDir()), newWorkerExecutor());
    }

    public PhotoboothService(Camera camera, SessionRepository sessionRepository, ExecutorService executor) {
        this(camera, sessionRepository, executor, AppConfig.get());
    }

    public PhotoboothService(Camera camera, SessionRepository sessionRepository, ExecutorService executor,
                             AppConfig config) {
        this(camera, sessionRepository, executor, ConfigStore.inMemory(config));
    }

    public PhotoboothService(Camera camera, SessionRepository sessionRepository, ExecutorService executor,
                             ConfigStore configStore) {
        if (camera == null) throw new IllegalArgumentException("camera tidak boleh null");
        if (sessionRepository == null) throw new IllegalArgumentException("sessionRepository tidak boleh null");
        if (executor == null) throw new IllegalArgumentException("executor tidak boleh null");
        this.camera = camera;
        this.sessionRepository = sessionRepository;
        this.executor = executor;
        this.templateFactory = new TemplateFactory();
        this.sessionManager = new SessionManager(sessionRepository, configStore);
        this.configStore = configStore;

        this.capturedImages = new ArrayList<>();
        this.availableTemplates = new HashMap<>();

        // Panggil factory untuk memuat template
        initializeTemplates();
    }

    /**
     * Facade untuk mode sidecar: kamera dikelola UI (getUserMedia), jadi kamera lokal tidak dipakai.
     */
    public static PhotoboothService forSidecar(AppConfig config) {
        return forSidecar(ConfigStore.inMemory(config));
    }

    public static PhotoboothService forSidecar(ConfigStore configStore) {
        return forSidecar(configStore, PrinterResolver.system());
    }

    /** Sidecar lengkap: ShareServer dinyalakan sesuai config, antrean cetak dengan resolver printer. */
    public static PhotoboothService forSidecar(ConfigStore configStore, PrinterResolver printers) {
        PhotoboothService service = forSidecarCore(configStore);
        service.shareService = new ShareService(configStore, configStore.current().sessionsDir(),
                Clock.systemDefaultZone());
        service.shareService.reconcile();
        service.printManager = new PrintManager(configStore, printers, Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "vandebooth-print");
            t.setDaemon(true);
            return t;
        }));
        return service;
    }

    private static PhotoboothService forSidecarCore(ConfigStore configStore) {
        Camera remote = new Camera() {
            @Override
            public BufferedImage capture() throws CameraException {
                throw new CameraException("Mode sidecar: kamera dikelola UI");
            }

            @Override
            public void close() {
            }
        };
        return new PhotoboothService(remote, new SessionRepository(configStore.current().sessionsDir()), newWorkerExecutor(),
                configStore);
    }

    private static ExecutorService newWorkerExecutor() {
        return Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "vandebooth-worker");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Mengisi daftar template default menggunakan Factory.
     */
    private void initializeTemplates() {
        StripTemplate vertical = templateFactory.createTemplate("TPL-V");
        StripTemplate horizontal = templateFactory.createTemplate("TPL-H");

        if (vertical != null) availableTemplates.put(vertical.getTemplateId(), vertical);
        if (horizontal != null) availableTemplates.put(horizontal.getTemplateId(), horizontal);

        System.out.println("LOG: 2 Template berhasil dimuat oleh factory.");
    }

    // --- METODE UTAMA UNTUK GUI ---

    /**
     * Mengambil satu gambar dari kamera.
     */
    public BufferedImage captureImage() throws CameraException {
        return camera.capture();
    }

    /**
     * Menambahkan gambar (yang sudah difilter) ke dalam list.
     */
    public void addCapturedImage(BufferedImage image) {
        if (image != null) {
            capturedImages.add(image);
        }
    }

    /**
     * Menghapus semua gambar yang sudah ditangkap.
     */
    public void clearCapturedImages() {
        capturedImages.clear();
        System.out.println("LOG: Daftar gambar dibersihkan.");
    }

    /**
     * Menggabungkan gambar yang sudah ditangkap menggunakan template yang dipilih (sinkron).
     */
    public BufferedImage generateStrip(String templateId) throws TemplateNotFoundException {
        return findTemplate(templateId).applyTemplate(capturedImages);
    }

    /**
     * Versi async dari {@link #generateStrip(String)}. Foto disalin saat dipanggil,
     * sehingga perubahan list setelahnya tidak memengaruhi hasil.
     */
    public CompletableFuture<BufferedImage> generateStripAsync(String templateId, ProgressListener listener) {
        ProgressListener progress = ProgressListener.orNone(listener);
        StripTemplate template;
        try {
            template = findTemplate(templateId);
        } catch (TemplateNotFoundException e) {
            return CompletableFuture.failedFuture(e);
        }
        ArrayList<BufferedImage> snapshot = new ArrayList<>(capturedImages);
        return CompletableFuture.supplyAsync(() -> {
            progress.onProgress(0, "Menyusun strip");
            BufferedImage strip = template.applyTemplate(snapshot);
            progress.onProgress(100, "Strip siap");
            return strip;
        }, executor);
    }

    /**
     * Menjalankan alur simpan di background: buat video strip (opsional), arsipkan sesi
     * lewat SessionRepository, lalu export dengan strategi yang dipilih.
     *
     * @param videoTask pembuat video strip, boleh null; kegagalannya tidak menggagalkan export
     * @param strategy  strategi export, boleh null (hanya arsip sesi)
     * @return folder sesi yang tersimpan
     */
    public CompletableFuture<Path> exportAsync(String templateId, BufferedImage strip, ExportStrategy strategy,
                                               Callable<File> videoTask, ProgressListener listener) {
        ProgressListener progress = ProgressListener.orNone(listener);
        List<BufferedImage> frames = new ArrayList<>(capturedImages);
        return CompletableFuture.supplyAsync(() -> {
            try {
                progress.onProgress(0, "Memulai penyimpanan");

                File video = null;
                if (videoTask != null) {
                    progress.onProgress(20, "Membuat video strip");
                    try {
                        video = videoTask.call();
                    } catch (Exception e) {
                        progress.onProgress(20, "Video strip dilewati: " + e.getMessage());
                    }
                }

                progress.onProgress(50, "Menyimpan arsip sesi");
                Path sessionDir = sessionRepository.save(new SessionRecord(
                        templateId, frames, strip, video == null ? null : video.toPath()));

                if (strategy != null) {
                    progress.onProgress(80, "Mengekspor ke " + strategy.getStrategyName());
                    if (!strategy.export(strip, video)) {
                        throw new ExportFailedException("Gagal mengekspor ke " + strategy.getStrategyName());
                    }
                }

                progress.onProgress(100, "Selesai");
                return sessionDir;
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, executor);
    }

    private StripTemplate findTemplate(String templateId) throws TemplateNotFoundException {
        StripTemplate template = availableTemplates.get(templateId);
        if (template == null) {
            throw new TemplateNotFoundException("Template tidak ditemukan: " + templateId);
        }
        return template;
    }

    /** Menghentikan worker background (tugas yang sedang berjalan dibiarkan selesai). */
    public void shutdown() {
        executor.shutdown();
        if (shareService != null) shareService.close();
        if (printManager != null) printManager.close();
    }

    // --- Sesi booth yang dikendalikan UI (sidecar Fase 2) ---

    public AppConfig getConfig() {
        return sessionManager.config();
    }

    public List<StripLayout> getLayouts() {
        return List.of(StripLayout.values());
    }

    public List<BoothFilter> getFilters() {
        return List.of(BoothFilter.values());
    }

    public String createSession(String layoutId) throws BoothException, IOException {
        return sessionManager.createSession(layoutId);
    }

    public String createSession(String layoutId, String continueFrom) throws BoothException, IOException {
        return sessionManager.createSession(layoutId, continueFrom);
    }

    public ConfigStore getConfigStore() {
        return configStore;
    }

    public SessionManager getSessionManager() {
        return sessionManager;
    }

    // --- Pembayaran demo ---

    public SessionManager.PaymentStart startPayment(String sessionId) throws BoothException, IOException {
        return sessionManager.startPayment(sessionId);
    }

    public PaymentStatus simulatePayment(String sessionId) throws BoothException, IOException {
        return sessionManager.simulatePayment(sessionId);
    }

    public PaymentStatus paymentStatus(String sessionId) throws BoothException {
        return sessionManager.paymentStatus(sessionId);
    }

    public boolean paymentRequired(String sessionId) throws BoothException {
        return sessionManager.paymentRequired(sessionId);
    }

    // --- Berbagi lewat jaringan lokal ---

    public ShareService getShareService() {
        if (shareService == null) throw new IllegalStateException("Berbagi hanya tersedia di mode sidecar");
        return shareService;
    }

    /** Link + QR unduh untuk strip sesi (strip harus sudah dikomposisi). */
    public ShareService.ShareLink share(String sessionId) throws BoothException {
        try {
            sessionManager.stripPath(sessionId);
        } catch (BoothException e) {
            if (e.kind() == BoothException.Kind.NOT_FOUND && e.getMessage().startsWith("Strip")) {
                throw new BoothException(BoothException.Kind.CONFLICT, "Strip belum dibuat, panggil compose dulu");
            }
            throw e;
        }
        return getShareService().linkForSession(sessionId);
    }

    // --- Cetak ---

    public PrintManager getPrintManager() {
        if (printManager == null) throw new IllegalStateException("Cetak hanya tersedia di mode sidecar");
        return printManager;
    }

    public PrintManager.JobStatus print(String sessionId, int copies) throws BoothException {
        Path strip;
        try {
            strip = sessionManager.stripPath(sessionId);
        } catch (BoothException e) {
            if (e.kind() == BoothException.Kind.NOT_FOUND && e.getMessage().startsWith("Strip")) {
                throw new BoothException(BoothException.Kind.CONFLICT, "Strip belum dibuat, panggil compose dulu");
            }
            throw e;
        }
        return getPrintManager().submit(sessionId, strip, copies);
    }

    public PrintManager.JobStatus printStatus(String sessionId) throws BoothException {
        sessionManager.paymentRequired(sessionId); // 404 bila sesi tidak ada
        return getPrintManager().status(sessionId);
    }

    public void putFrame(String sessionId, int index, byte[] jpeg) throws BoothException, IOException {
        sessionManager.putFrame(sessionId, index, jpeg);
    }

    public byte[] getFrame(String sessionId, int index) throws BoothException {
        return sessionManager.getFrame(sessionId, index);
    }

    public SessionManager.ComposeResult compose(String sessionId, String filterId) throws BoothException, IOException {
        return sessionManager.compose(sessionId, filterId);
    }

    public Path getStripPath(String sessionId) throws BoothException {
        return sessionManager.stripPath(sessionId);
    }

    public Path exportLocal(String sessionId) throws BoothException, IOException {
        return sessionManager.exportLocal(sessionId);
    }

    public void abandon(String sessionId) throws BoothException, IOException {
        sessionManager.abandon(sessionId);
    }

    // --- Getter untuk GUI ---

    public ArrayList<BufferedImage> getCapturedImages() {
        return capturedImages;
    }

    public Map<String, StripTemplate> getAvailableTemplates() {
        return availableTemplates;
    }

    public SessionRepository getSessionRepository() {
        return sessionRepository;
    }

    /**
     * Akses webcam untuk live preview GUI. Hanya tersedia bila service memakai CameraManager.
     */
    public CameraManager getCameraManager() {
        if (camera instanceof CameraManager) {
            return (CameraManager) camera;
        }
        throw new IllegalStateException("Service tidak memakai CameraManager");
    }
}
