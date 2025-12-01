package service;

import hardware.CameraManager; // Singleton
import factory.TemplateFactory; // Factory
import export.ExportStrategy; // Strategy
import model.StripTemplate;
import exception.TemplateNotFoundException;
import exception.ExportFailedException;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Ini adalah "otak" bisnis dari aplikasi Photobooth.
 * VERSI UPDATE: Memuat 6 template.
 */
public class PhotoboothService {

    // 1. Referensi ke semua Design Pattern
    private CameraManager cameraManager;
    private TemplateFactory templateFactory;
    
    // 2. Daftar gambar yang ditangkap
    private ArrayList<BufferedImage> capturedImages;
    
    // 3. Daftar template yang tersedia (untuk GUI)
    private Map<String, StripTemplate> availableTemplates;

    // Constructor
    public PhotoboothService() {
        // Ambil instance dari Singleton dan Factory
        this.cameraManager = CameraManager.getInstance();
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
    public BufferedImage captureImage() {
        BufferedImage image = cameraManager.takePicture();
        return image;
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
    public void saveFinalImage(ExportStrategy strategy, BufferedImage finalImage) throws ExportFailedException {
        System.out.println("LOG: Service memanggil " + strategy.getStrategyName());
        boolean success = strategy.export(finalImage);
        
        if (!success) {
            throw new ExportFailedException("Gagal mengekspor gambar.");
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
    
    public CameraManager getCameraManager() {
        return cameraManager;
    }
}