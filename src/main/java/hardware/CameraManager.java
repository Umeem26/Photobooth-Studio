package hardware;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamResolution;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;

// Ini adalah Design Pattern #2: Singleton (versi baru)
public class CameraManager {
    
    // 1. Satu-satunya instance (static)
    private static CameraManager instance;
    
    // 2. Objek hardware yang dipegang oleh Singleton
    private Webcam webcam;

    // 3. Constructor dibuat PRIVATE
    private CameraManager() {
        System.out.println("LOG: CameraManager diinisialisasi...");
        try {
            // Mengambil webcam default
            webcam = Webcam.getDefault();
            
            if (webcam == null) {
                System.err.println("FATAL: Tidak ada webcam ditemukan.");
                // Nanti kita ganti dengan throw new CameraNotFoundException()
                return; 
            }
            
            // Atur resolusi (bisa disesuaikan)
            Dimension size = WebcamResolution.VGA.getSize();
            webcam.setViewSize(size);

            // Buka koneksi ke kamera
            if (!webcam.open()) {
                System.err.println("FATAL: Gagal membuka kamera.");
                // Nanti kita ganti dengan throw new CameraException()
            }
            
        } catch (Exception e) {
            System.err.println("FATAL: Error inisialisasi kamera: " + e.getMessage());
        }
    }

    // 4. Method public (static) untuk mendapatkan satu-satunya instance
    public static CameraManager getInstance() {
        if (instance == null) {
            instance = new CameraManager();
        }
        return instance;
    }

    // 5. Method utama untuk mengambil gambar
    public BufferedImage takePicture() {
        if (webcam == null || !webcam.isOpen()) {
            System.err.println("ERROR: Kamera tidak siap.");
            return null;
        }
        
        System.out.println("LOG: Mengambil gambar...");
        return webcam.getImage();
    }
    
    // Method untuk GUI (live preview)
    public Webcam getWebcam() {
        return this.webcam;
    }

    // Method untuk menutup kamera saat aplikasi ditutup
    public void closeCamera() {
        if (webcam != null && webcam.isOpen()) {
            webcam.close();
            System.out.println("LOG: Kamera ditutup.");
        }
    }

        /**
     * Mengembalikan daftar semua webcam yang terdeteksi.
     */
    public List<Webcam> getDetectedWebcams() {
        return Webcam.getWebcams();
    }

    /**
     * Ganti kamera yang digunakan seluruh aplikasi.
     * Dipakai oleh GUI ketika user memilih kamera berbeda dari dropdown.
     */
    public void switchToWebcam(Webcam newWebcam) {
        if (newWebcam == null) return;
        if (this.webcam == newWebcam) return;

        // Tutup kamera lama
        if (this.webcam != null && this.webcam.isOpen()) {
            this.webcam.close();
        }

        // Set kamera baru
        this.webcam = newWebcam;
        this.webcam.setViewSize(WebcamResolution.VGA.getSize());
        this.webcam.open(true);

        System.out.println("LOG: Berpindah ke kamera: " + this.webcam.getName());
    }

}