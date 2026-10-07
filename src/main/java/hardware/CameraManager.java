package hardware;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamResolution;
import exception.CameraException;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Singleton (thread-safe, initialization-on-demand holder) pengendali webcam.
 * Webcam baru dicari saat pertama dibutuhkan dan baru dibuka saat capture,
 * sehingga membuat instance tidak menyentuh hardware.
 */
public final class CameraManager implements Camera {

    private static final class Holder {
        private static final CameraManager INSTANCE = new CameraManager();
    }

    private Webcam webcam;

    private CameraManager() {
    }

    public static CameraManager getInstance() {
        return Holder.INSTANCE;
    }

    @Override
    public synchronized BufferedImage capture() throws CameraException {
        Webcam cam = requireWebcam();
        if (!cam.isOpen()) {
            boolean opened;
            try {
                opened = cam.open();
            } catch (RuntimeException e) {
                throw new CameraException("Gagal membuka kamera: " + cam.getName(), e);
            }
            if (!opened) {
                throw new CameraException("Gagal membuka kamera: " + cam.getName());
            }
        }
        BufferedImage image = cam.getImage();
        if (image == null) {
            throw new CameraException("Kamera tidak mengembalikan gambar: " + cam.getName());
        }
        return image;
    }

    /**
     * Webcam aktif untuk live preview GUI (dicari lazy, belum tentu terbuka).
     * Mengembalikan null bila tidak ada webcam terdeteksi.
     */
    public synchronized Webcam getWebcam() {
        if (webcam == null) {
            try {
                webcam = resolveDefault();
            } catch (CameraException e) {
                return null;
            }
        }
        return webcam;
    }

    private Webcam requireWebcam() throws CameraException {
        if (webcam == null) {
            webcam = resolveDefault();
        }
        return webcam;
    }

    private static Webcam resolveDefault() throws CameraException {
        Webcam found;
        try {
            found = Webcam.getDefault();
        } catch (RuntimeException e) {
            throw new CameraException("Gagal mendeteksi webcam", e);
        }
        if (found == null) {
            throw new CameraException("Tidak ada webcam ditemukan");
        }
        if (!found.isOpen()) {
            found.setViewSize(WebcamResolution.VGA.getSize());
        }
        return found;
    }

    @Override
    public synchronized void close() {
        if (webcam != null && webcam.isOpen()) {
            webcam.close();
        }
    }

    /** Alias lama yang dipakai GUI saat jendela ditutup. */
    public void closeCamera() {
        close();
    }

    /** Daftar semua webcam yang terdeteksi. */
    public List<Webcam> getDetectedWebcams() {
        return Webcam.getWebcams();
    }

    /**
     * Ganti kamera yang digunakan seluruh aplikasi (dipakai dropdown kamera di GUI).
     */
    public synchronized void switchToWebcam(Webcam newWebcam) {
        if (newWebcam == null || this.webcam == newWebcam) return;

        if (this.webcam != null && this.webcam.isOpen()) {
            this.webcam.close();
        }
        this.webcam = newWebcam;
        if (!newWebcam.isOpen()) {
            newWebcam.setViewSize(WebcamResolution.VGA.getSize());
        }
        newWebcam.open(true);
    }
}
