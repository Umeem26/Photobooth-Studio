package utils;

import org.jcodec.api.FrameGrab;
import org.jcodec.api.awt.AWTSequenceEncoder;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.io.SeekableByteChannel;
import org.jcodec.common.model.Picture;
import org.jcodec.scale.AWTUtil;

import model.StripTemplate;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

/**
 * Util untuk mengekspor satu video strip dari beberapa
 * video sesi (1 video per foto) dengan layout sesuai StripTemplate.
 *
 * Setelah selesai, file video sesi akan DIHAPUS.
 */
public class StripVideoExporter {

    private static final int STRIP_REPEAT = 2;
    public static File exportStripVideo(File[] videoFiles, int maxPhotos, StripTemplate template) throws IOException {
        if (videoFiles == null || template == null) return null;

        // Pastikan semua sesi punya video
        for (int i = 0; i < maxPhotos; i++) {
            if (i >= videoFiles.length || videoFiles[i] == null || !videoFiles[i].exists()) {
                return null; // incomplete, gak usah bikin video
            }
        }

        File folder = new File("../HasilPhotobooth/Video");
        if (!folder.exists()) folder.mkdirs();

        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        File outputFile = new File(folder, "strip_" + timestamp + ".mp4");

        SeekableByteChannel[] channels = new SeekableByteChannel[maxPhotos];
        FrameGrab[] grabs = new FrameGrab[maxPhotos];
        boolean[] finished = new boolean[maxPhotos];

        AWTSequenceEncoder encoder = AWTSequenceEncoder.createSequenceEncoder(outputFile, VideoRecorder.FPS);

        try {
            // buka semua video sesi
            for (int i = 0; i < maxPhotos; i++) {
                channels[i] = NIOUtils.readableFileChannel(videoFiles[i].getPath());
                try {
                    grabs[i] = FrameGrab.createFrameGrab(channels[i]);
                } catch (org.jcodec.api.JCodecException e) {
                    // bungkus jadi IOException supaya sesuai signature method
                    throw new IOException("Gagal inisialisasi FrameGrab untuk video sesi ke-" + (i + 1), e);
                }
            }

            while (true) {
                boolean anyFrame = false;
                boolean stopAll = false;                    // <<< NEW
                BufferedImage[] frames = new BufferedImage[maxPhotos];

                for (int i = 0; i < maxPhotos; i++) {
                    if (finished[i]) continue;
                    Picture pic = grabs[i].getNativeFrame();
                    if (pic == null) {
                        finished[i] = true;
                        stopAll = true;                     // <<< begitu ada yang habis, tandai
                    } else {
                        frames[i] = AWTUtil.toBufferedImage(pic);
                        anyFrame = true;
                    }
                }

                if (!anyFrame || stopAll) break;            // <<< stop di video terpendek

                ArrayList<BufferedImage> list = new ArrayList<>();
                for (int i = 0; i < maxPhotos; i++) {
                    if (frames[i] != null) list.add(frames[i]);
                }
                if (list.isEmpty()) continue;

                BufferedImage stripImage = template.applyTemplate(list);

                // encode frame strip yang sama beberapa kali
                for (int r = 0; r < STRIP_REPEAT; r++) {
                    encoder.encodeImage(stripImage);
                }
            }

            encoder.finish();
        } finally {
            for (int i = 0; i < maxPhotos; i++) {
                NIOUtils.closeQuietly(channels[i]);
            }
        }

        // Hapus semua file video sesi (output akhirnya cuma strip_)
        for (int i = 0; i < maxPhotos; i++) {
            if (i < videoFiles.length && videoFiles[i] != null && videoFiles[i].exists()) {
                videoFiles[i].delete();
            }
        }

        System.out.println("[StripVideoExporter] Saved -> " + outputFile.getAbsolutePath());
        return outputFile;
    }
}
