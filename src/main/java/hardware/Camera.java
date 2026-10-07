package hardware;

import exception.CameraException;
import java.awt.image.BufferedImage;

/**
 * Abstraksi sumber gambar, agar PhotoboothService bisa diuji tanpa webcam.
 */
public interface Camera {

    /** Mengambil satu frame. Tidak pernah mengembalikan null. */
    BufferedImage capture() throws CameraException;

    /** Melepas perangkat. Aman dipanggil berkali-kali. */
    void close();
}
