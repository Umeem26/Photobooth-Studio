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

public class VideoPreviewWindow extends JWindow {

    private JLabel videoLabel = new JLabel();
    private static final int FPS = VideoRecorder.FPS;  // <<< SAMA dengan encoder

    public VideoPreviewWindow(File videoFile) {
        if (videoFile == null) return;

        setLayout(new BorderLayout());
        getContentPane().setBackground(Color.BLACK);

        videoLabel.setHorizontalAlignment(SwingConstants.CENTER);
        add(videoLabel, BorderLayout.CENTER);

        setSize(500, 400);
        setLocationRelativeTo(null);
        setAlwaysOnTop(true);
        setVisible(true);

        new Thread(() -> playVideo(videoFile)).start();
    }

    private void playVideo(File file) {
        SeekableByteChannel ch = null;
        try {
            ch = NIOUtils.readableFileChannel(file.getPath());

            FrameGrab grab = FrameGrab.createFrameGrab(ch);
            Picture picture;

            while ((picture = grab.getNativeFrame()) != null) {
                final BufferedImage img = AWTUtil.toBufferedImage(picture);

                SwingUtilities.invokeLater(() ->
                        videoLabel.setIcon(new ImageIcon(img))
                );

                Thread.sleep(1000 / FPS); // sesuai FPS encoder
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            NIOUtils.closeQuietly(ch);
            SwingUtilities.invokeLater(this::dispose);
        }
    }
}
