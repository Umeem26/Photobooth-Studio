package service;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

import exception.CameraException;
import hardware.Camera;
import hardware.CameraManager;
import java.awt.image.BufferedImage;

class PhotoboothServiceCameraTest {

    @Test
    void captureImageDelegatesToInjectedCamera() throws Exception {
        BufferedImage frame = new BufferedImage(64, 48, BufferedImage.TYPE_INT_RGB);
        PhotoboothService service = new PhotoboothService(new FixedCamera(frame));
        assertSame(frame, service.captureImage());
    }

    @Test
    void captureImagePropagatesCameraException() {
        Camera broken = new Camera() {
            @Override public BufferedImage capture() throws CameraException {
                throw new CameraException("Tidak ada webcam ditemukan");
            }
            @Override public void close() { }
        };
        PhotoboothService service = new PhotoboothService(broken);
        CameraException ex = assertThrows(CameraException.class, service::captureImage);
        assertEquals("Tidak ada webcam ditemukan", ex.getMessage());
    }

    @Test
    void rejectsNullCamera() {
        assertThrows(IllegalArgumentException.class, () -> new PhotoboothService(null));
    }

    @Test
    void getCameraManagerOnlyAvailableWithRealCameraManager() {
        PhotoboothService fake = new PhotoboothService(new FixedCamera(null));
        assertThrows(IllegalStateException.class, fake::getCameraManager);
        assertSame(CameraManager.getInstance(), new PhotoboothService().getCameraManager());
    }

    static final class FixedCamera implements Camera {
        private final BufferedImage frame;
        FixedCamera(BufferedImage frame) { this.frame = frame; }
        @Override public BufferedImage capture() { return frame; }
        @Override public void close() { }
    }
}
