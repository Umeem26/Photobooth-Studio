package service;

import hardware.Camera;
import hardware.CameraManager; // Singleton
import factory.TemplateFactory; // Factory
import export.ExportStrategy; // Strategy
import model.StripTemplate;
import exception.TemplateNotFoundException;
import exception.ExportFailedException;
import exception.CameraException;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.io.File;

/**
 * Ini adalah "otak" bisnis dari aplikasi Photobooth.
 * VERSI UPDATE: Memuat 6 template.
 */
public class PhotoboothService {

    // 1. Referensi ke semua Design Pattern
    private final Camera camera;
    private TemplateFactory templateFactory;
    
    // 2. Daftar gambar yang ditangkap
    private ArrayList<BufferedImage> capturedImages;
    
    // 3. Daftar template yang tersedia (untuk GUI)
    private Map<String, StripTemplate> availableTemplates;

    // Constructor default: memakai webcam asli (Singleton, dibuka lazy)
    public PhotoboothService() {
        this(CameraManager.getInstance());
    }

    // Constructor untuk injeksi kamera (mis. kamera palsu di test)
    public PhotoboothService(Camera camera) {
        if (camera == null) throw new IllegalArgumentException("camera tidak boleh null");
        this.camera = camera;
        this.templateFactory = new TemplateFactory();
        
        this.capturedImages = new ArrayList<>();
        this.availableTemplates = new HashMap<>();
        
        // Panggil factory untuk memuat template
        initializeTemplates();
    }

    /**
     * Mengisi daftar template yang tersedia menggunakan Factory.
     * (Sudah di-update untuk 6 template)
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
     * Menggabungkan gambar yang sudah ditangkap menggunakan template yang dipilih.
     */
    public BufferedImage generateStrip(String templateId) throws TemplateNotFoundException {
        if (!availableTemplates.containsKey(templateId)) {
            throw new TemplateNotFoundException("Template tidak ditemukan: " + templateId);
        }
        
        StripTemplate template = availableTemplates.get(templateId);
        return template.applyTemplate(capturedImages);
    }

    /**
     * Menyimpan gambar final menggunakan strategi ekspor yang dipilih.
     */
    public void saveFinalImage(ExportStrategy strategy, BufferedImage finalImage, File videoFile) throws ExportFailedException {
        System.out.println("LOG: Service memanggil " + strategy.getStrategyName());
        
        // Panggil method export yang baru (dengan 2 parameter)
        boolean success = strategy.export(finalImage, videoFile);
        
        if (!success) {
            throw new ExportFailedException("Gagal mengekspor data.");
        }
    }
    
    // --- Getter untuk GUI ---
    
    public ArrayList<BufferedImage> getCapturedImages() {
        return capturedImages;
    }
    
    public Map<String, StripTemplate> getAvailableTemplates() {
        // Ini akan mengembalikan 6 template
        return availableTemplates;
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