package utils;

import org.jcodec.api.awt.AWTSequenceEncoder;   // <<< PERHATIKAN INI

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * MP4 video recorder menggunakan JCodec (AWTSequenceEncoder).
 * Dipakai saat hitungan mundur 3-2-1 di PhotoboothGUI.
 */
public class VideoRecorder {

    private AWTSequenceEncoder encoder;
    private boolean recording = false;
    private File outputFile;

    /**
     * Mulai rekaman video baru di folder ../HasilPhotobooth/Video
     */
    public void startRecording() {
        try {
            File folder = new File("../HasilPhotobooth/Video");
            if (!folder.exists()) {
                folder.mkdirs();
            }

            String timestamp = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

            outputFile = new File(folder, "countdown_" + timestamp + ".mp4");

            // Versi AWTSequenceEncoder di jcodec-javase yg kamu pakai
            encoder = AWTSequenceEncoder.createSequenceEncoder(outputFile, 1);
            recording = true;

            System.out.println("[VideoRecorder] Start → " + outputFile.getAbsolutePath());

        } catch (IOException e) {
            e.printStackTrace();
            recording = false;
            encoder = null;
            outputFile = null;
        }
    }

    /**
     * Tambah 1 frame ke video (dipanggil terus saat countdown berjalan).
     */
    public void addFrame(BufferedImage frame) {
        if (!recording || encoder == null || frame == null) return;
        try {
            encoder.encodeImage(frame);   // method ini ada di AWTSequenceEncoder
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Stop rekaman dan kembalikan File video yang dihasilkan.
     */
    public File stopRecording() {
        if (!recording || encoder == null) return null;

        try {
            encoder.finish();
            recording = false;

            System.out.println("[VideoRecorder] Saved → " + outputFile.getAbsolutePath());
            return outputFile;

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        } finally {
            encoder = null;
        }
    }

    public boolean isRecording() {
        return recording;
    }
}
