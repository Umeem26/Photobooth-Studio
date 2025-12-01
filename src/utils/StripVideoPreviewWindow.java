package utils;

import org.jcodec.api.FrameGrab;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.io.SeekableByteChannel;
import org.jcodec.common.model.Picture;
import org.jcodec.scale.AWTUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;

import model.StripTemplate;

/**
 * Preview gabungan video berdasarkan StripTemplate yang sama
 * dengan template cetak (Classic, Vertical, Horizontal, dll).
 */
public class StripVideoPreviewWindow extends JWindow {

    private JLabel videoLabel = new JLabel();

    private static final int FPS = VideoRecorder.FPS;
    private File[] videoFiles;
    private int maxPhotos;
    private StripTemplate template;
    private boolean sizeInitialized = false;   // <-- untuk atur size sekali saja

    public StripVideoPreviewWindow(File[] videoFiles, int maxPhotos, StripTemplate template) {
        this.maxPhotos = maxPhotos;
        this.template = template;

        // copy array supaya tidak mengubah referensi asli
        this.videoFiles = new File[maxPhotos];
        for (int i = 0; i < maxPhotos; i++) {
            this.videoFiles[i] = (videoFiles != null && i < videoFiles.length) ? videoFiles[i] : null;
        }

        setLayout(new BorderLayout());
        getContentPane().setBackground(Color.BLACK);

        videoLabel.setHorizontalAlignment(SwingConstants.CENTER);
        add(videoLabel, BorderLayout.CENTER);

        // BELUM setSize di sini, kita sesuaikan setelah frame pertama
        setAlwaysOnTop(true);
        setVisible(true);

        new Thread(this::playStrip).start();
    }

    private void playStrip() {
        SeekableByteChannel[] channels = new SeekableByteChannel[maxPhotos];
        FrameGrab[] grabs = new FrameGrab[maxPhotos];
        boolean[] finished = new boolean[maxPhotos];

        try {
            // buka semua video
            for (int i = 0; i < maxPhotos; i++) {
                if (videoFiles[i] != null && videoFiles[i].exists()) {
                    channels[i] = NIOUtils.readableFileChannel(videoFiles[i].getPath());
                    grabs[i] = FrameGrab.createFrameGrab(channels[i]);
                } else {
                    finished[i] = true;
                }
            }

            while (true) {
                boolean anyFrame = false;
                BufferedImage[] frames = new BufferedImage[maxPhotos];

                // ambil 1 frame dari tiap video
                for (int i = 0; i < maxPhotos; i++) {
                    if (grabs[i] == null || finished[i]) continue;

                    Picture pic = grabs[i].getNativeFrame();
                    if (pic == null) {
                        finished[i] = true;
                    } else {
                        frames[i] = AWTUtil.toBufferedImage(pic);
                        anyFrame = true;
                    }
                }

                if (!anyFrame) break; // semua video habis

                // ubah ke list sesuai urutan template
                ArrayList<BufferedImage> frameList = new ArrayList<>();
                for (int i = 0; i < maxPhotos; i++) {
                    if (frames[i] != null) {
                        frameList.add(frames[i]);
                    }
                }
                if (frameList.isEmpty()) {
                    continue;
                }

                // generate strip sesuai template aslinya
                BufferedImage stripImage = template.applyTemplate(frameList);

                // scale supaya tidak terlalu besar tapi tetap jaga rasio
                BufferedImage scaled = scaleToFit(stripImage, 880, 660);

                // Atur ukuran window hanya di frame pertama
                if (!sizeInitialized) {
                    sizeInitialized = true;
                    int winW = scaled.getWidth() + 40;  // sedikit padding
                    int winH = scaled.getHeight() + 60;
                    SwingUtilities.invokeLater(() -> {
                        setSize(winW, winH);
                        setLocationRelativeTo(null);
                    });
                }

                SwingUtilities.invokeLater(() ->
                        videoLabel.setIcon(new ImageIcon(scaled))
                );

                try {
                    Thread.sleep(1000 / FPS);
                } catch (InterruptedException ignored) { }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            for (int i = 0; i < maxPhotos; i++) {
                NIOUtils.closeQuietly(channels[i]);
            }
            SwingUtilities.invokeLater(this::dispose);
        }
    }

    /**
     * Men-scale gambar agar muat di dalam maxW x maxH
     * tanpa mengubah rasio (aspect ratio).
     */
    private BufferedImage scaleToFit(BufferedImage src, int maxW, int maxH) {
        int w = src.getWidth();
        int h = src.getHeight();
        double scale = Math.min(
                (double) maxW / w,
                (double) maxH / h
        );
        if (scale >= 1.0) {
            // sudah cukup kecil, tidak perlu diskalakan
            return src;
        }
        int nw = (int) Math.round(w * scale);
        int nh = (int) Math.round(h * scale);

        BufferedImage dst = new BufferedImage(nw, nh, src.getType());
        Graphics2D g = dst.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(src, 0, 0, nw, nh, null);
        g.dispose();
        return dst;
    }
}