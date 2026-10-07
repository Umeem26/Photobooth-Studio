package utils;

import org.jcodec.api.awt.AWTSequenceEncoder;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class VideoRecorder {

    public static final int FPS = 10;   // <<< di sini atur kecepatan video

    private AWTSequenceEncoder encoder;
    private boolean recording = false;
    private File outputFile;

    public void startRecording() {
        try {
            File folder = new File("../HasilPhotobooth/Video");
            if (!folder.exists()) {
                folder.mkdirs();
            }

            String timestamp = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

            outputFile = new File(folder, "countdown_" + timestamp + ".mp4");

            // encode dengan FPS yang sama dengan preview
            encoder = AWTSequenceEncoder.createSequenceEncoder(outputFile, FPS);
            recording = true;

            System.out.println("[VideoRecorder] Start → " + outputFile.getAbsolutePath());

        } catch (IOException e) {
            e.printStackTrace();
            recording = false;
            encoder = null;
            outputFile = null;
        }
    }

    public void addFrame(BufferedImage frame) {
        if (!recording || encoder == null || frame == null) return;
        try {
            encoder.encodeImage(frame);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

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
