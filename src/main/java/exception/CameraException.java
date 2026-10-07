package exception;

/**
 * Kegagalan akses kamera: tidak ada webcam, gagal dibuka, atau gagal mengambil gambar.
 */
public class CameraException extends Exception {
    public CameraException(String message) {
        super(message);
    }

    public CameraException(String message, Throwable cause) {
        super(message, cause);
    }
}
