package utils;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.Desktop;

/**
 * Popup preview untuk video hasil rekaman countdown.
 * Menampilkan path file dan tombol untuk membuka di pemutar video default OS.
 */
public class VideoPreviewWindow extends JWindow {

    public VideoPreviewWindow(File videoFile) {
        if (videoFile == null) return;

        setLayout(new BorderLayout(10, 10));
        getContentPane().setBackground(new Color(25, 25, 25));

        JLabel title = new JLabel("Video countdown tersimpan!", SwingConstants.CENTER);
        title.setForeground(Color.WHITE);
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));

        JLabel path = new JLabel(videoFile.getAbsolutePath(), SwingConstants.CENTER);
        path.setForeground(Color.LIGHT_GRAY);
        path.setFont(new Font("Segoe UI", Font.PLAIN, 11));

        JButton btnOpen = new JButton("Buka di pemutar video");
        btnOpen.setFocusPainted(false);
        btnOpen.setBackground(new Color(0, 120, 215));
        btnOpen.setForeground(Color.WHITE);
        btnOpen.setFont(new Font("Segoe UI", Font.PLAIN, 14));

        btnOpen.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!Desktop.isDesktopSupported()) {
                    JOptionPane.showMessageDialog(
                            VideoPreviewWindow.this,
                            "Desktop API tidak didukung di sistem ini.",
                            "Error",
                            JOptionPane.ERROR_MESSAGE
                    );
                    return;
                }
                try {
                    Desktop.getDesktop().open(videoFile);
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(
                            VideoPreviewWindow.this,
                            "Tidak dapat membuka video: " + ex.getMessage(),
                            "Error",
                            JOptionPane.ERROR_MESSAGE
                    );
                }
            }
        });

        JPanel center = new JPanel(new GridLayout(2, 1, 5, 5));
        center.setOpaque(false);
        center.add(title);
        center.add(path);

        JPanel south = new JPanel();
        south.setOpaque(false);
        south.add(btnOpen);

        add(center, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);

        setSize(500, 150);
        setLocationRelativeTo(null);
        setAlwaysOnTop(true);
        setVisible(true);
    }
}