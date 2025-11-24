package gui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

import service.PhotoboothService;
import model.StripTemplate;
import export.ExportStrategy;
import export.LocalExportStrategy;
import export.DriveExportStrategy;

import com.github.sarxos.webcam.WebcamPanel;
import com.github.sarxos.webcam.WebcamPanel.Painter; 

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;

import filter.FilterStrategy;
import filter.GrayscaleFilterStrategy;
import filter.NoFilterStrategy;

public class PhotoboothGUI extends JFrame {

    private PhotoboothService service;

    // Komponen GUI
    private WebcamPanel webcamPanel;
    private JPanel pnlGallery;
    private JButton btnCapture;
    private JButton btnSave;
    private JComboBox<String> comboTemplate;
    private JComboBox<String> comboExport;
    private JComboBox<String> comboFilter;
    
    private Map<String, FilterStrategy> filterStrategies;
    private JLabel[] galleryLabels;
    private Timer countdownTimer;
    private int countdownValue;
    private int currentCaptureSlot = 0;
    private CountdownPainter countdownPainter;
    private String selectedTemplateId;
    private int maxPhotos;

    // Warna Tema
    private final Color PRIMARY_COLOR = new Color(0, 120, 215); 
    private final Color SUCCESS_COLOR = new Color(30, 160, 80); 
    private final Font TITLE_FONT = new Font("Segoe UI", Font.BOLD, 24);
    private final Font UI_FONT = new Font("Segoe UI", Font.PLAIN, 14);

    public PhotoboothGUI(PhotoboothService service, String selectedTemplateId) {
        this.service = service;
        this.selectedTemplateId = selectedTemplateId;

        StripTemplate template = service.getAvailableTemplates().get(selectedTemplateId);
        this.maxPhotos = (template != null) ? template.getMaxPhotos() : 4;

        setTitle("Photobooth Studio Pro");
        setSize(1100, 750);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));
        getContentPane().setBackground(new Color(30, 30, 30));

        initializeFilters();

        add(createHeaderPanel(), BorderLayout.NORTH);

        webcamPanel = new WebcamPanel(service.getCameraManager().getWebcam());
        webcamPanel.setMirrored(true);
        Painter defaultPainter = webcamPanel.getPainter();
        countdownPainter = new CountdownPainter(defaultPainter);
        webcamPanel.setPainter(countdownPainter);
        
        JPanel webcamContainer = new JPanel(new BorderLayout());
        webcamContainer.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY, 2));
        webcamContainer.add(webcamPanel, BorderLayout.CENTER);
        add(webcamContainer, BorderLayout.CENTER);

        add(createActionPanel(), BorderLayout.SOUTH);

        pnlGallery = createGalleryPanel();
        add(pnlGallery, BorderLayout.EAST);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                service.clearCapturedImages();
                service.getCameraManager().closeCamera();
                TemplateSelectionGUI selectionGUI = new TemplateSelectionGUI(service);
                selectionGUI.setVisible(true);
            }
        });
        
        updateGallery();
        webcamPanel.start();
    }

    private void initializeFilters() {
        filterStrategies = new HashMap<>();
        FilterStrategy noFilter = new NoFilterStrategy();
        FilterStrategy grayFilter = new GrayscaleFilterStrategy();
        filterStrategies.put(noFilter.getFilterName(), noFilter);
        filterStrategies.put(grayFilter.getFilterName(), grayFilter);
    }

    private JPanel createHeaderPanel() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(45, 45, 48));
        header.setBorder(new EmptyBorder(15, 20, 15, 20));

        JLabel title = new JLabel("PHOTOBOOTH STUDIO");
        title.setFont(TITLE_FONT);
        title.setForeground(Color.WHITE);
        // Gunakan helper loadIcon untuk ikon header juga
        title.setIcon(loadIcon("camera.png", 32)); 
        title.setIconTextGap(15);

        JLabel subtitle = new JLabel("Template: " + this.selectedTemplateId);
        subtitle.setFont(new Font("Segoe UI", Font.ITALIC, 14));
        subtitle.setForeground(Color.LIGHT_GRAY);

        header.add(title, BorderLayout.WEST);
        header.add(subtitle, BorderLayout.EAST);
        return header;
    }
    
    private JPanel createGalleryPanel() {
        JPanel panel = new JPanel(new GridLayout(maxPhotos, 1, 10, 10));
        panel.setBackground(new Color(30, 30, 30));
        panel.setBorder(new EmptyBorder(0, 10, 10, 10));
        panel.setPreferredSize(new Dimension(220, 0));

        galleryLabels = new JLabel[maxPhotos];
        for (int i = 0; i < maxPhotos; i++) {
            galleryLabels[i] = new JLabel("Slot " + (i + 1), SwingConstants.CENTER);
            galleryLabels[i].setFont(UI_FONT);
            galleryLabels[i].setForeground(Color.GRAY);
            galleryLabels[i].setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.DARK_GRAY, 1),
                BorderFactory.createMatteBorder(5, 5, 20, 5, new Color(40, 40, 40))
            ));
            galleryLabels[i].setOpaque(true);
            galleryLabels[i].setBackground(new Color(20, 20, 20));
            panel.add(galleryLabels[i]);
        }
        return panel;
    }

    // --- BAGIAN INI DIPERBARUI UNTUK IKON ---
    private JPanel createActionPanel() {
        JPanel mainActionPanel = new JPanel(new BorderLayout(10, 10));
        mainActionPanel.setBackground(new Color(30, 30, 30));
        mainActionPanel.setBorder(new EmptyBorder(10, 20, 20, 20));

        // Tombol AMBIL FOTO
        btnCapture = new JButton("AMBIL FOTO (1/" + maxPhotos + ")");
        styleButton(btnCapture, PRIMARY_COLOR);
        // Muat ikon camera.png, resize ke 24px
        btnCapture.setIcon(loadIcon("camera.png", 24)); 
        btnCapture.addActionListener(e -> startSingleCaptureCountdown());

        // Tombol SIMPAN
        btnSave = new JButton("SIMPAN STRIP");
        styleButton(btnSave, SUCCESS_COLOR);
        btnSave.setEnabled(false);
        // Muat ikon save.png, resize ke 24px
        btnSave.setIcon(loadIcon("save.png", 24));
        btnSave.addActionListener(e -> saveStripProcess());

        // Dropdowns
        comboFilter = new JComboBox<>();
        for (String filterName : filterStrategies.keySet()) comboFilter.addItem(filterName);
        styleComboBox(comboFilter);

        comboExport = new JComboBox<>(new String[]{"Komputer", "Google Drive"});
        styleComboBox(comboExport);

        JPanel buttonPanel = new JPanel(new GridLayout(1, 2, 20, 0));
        buttonPanel.setOpaque(false);
        buttonPanel.add(btnCapture);
        buttonPanel.add(btnSave);

        JPanel optionsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 0));
        optionsPanel.setOpaque(false);
        
        JPanel filterP = new JPanel(new BorderLayout(5, 0));
        filterP.setOpaque(false);
        JLabel lblFilter = new JLabel("Filter Efek:");
        lblFilter.setFont(UI_FONT);
        filterP.add(lblFilter, BorderLayout.NORTH);
        filterP.add(comboFilter, BorderLayout.CENTER);
        
        JPanel exportP = new JPanel(new BorderLayout(5, 0));
        exportP.setOpaque(false);
        JLabel lblExport = new JLabel("Simpan Ke:");
        lblExport.setFont(UI_FONT);
        exportP.add(lblExport, BorderLayout.NORTH);
        exportP.add(comboExport, BorderLayout.CENTER);

        optionsPanel.add(filterP);
        optionsPanel.add(exportP);

        mainActionPanel.add(buttonPanel, BorderLayout.NORTH);
        mainActionPanel.add(optionsPanel, BorderLayout.CENTER);
        
        return mainActionPanel;
    }

    // --- HELPER BARU: Memuat & Resize Ikon ---
    /**
     * Memuat gambar dari file, mengubah ukurannya, dan mengembalikannya sebagai ImageIcon.
     * Jika file tidak ada, mengembalikan null (tidak error/crash).
     */
    private ImageIcon loadIcon(String path, int size) {
        try {
            File imgFile = new File(path);
            if (imgFile.exists()) {
                ImageIcon originalIcon = new ImageIcon(path);
                Image img = originalIcon.getImage();
                Image newImg = img.getScaledInstance(size, size, Image.SCALE_SMOOTH);
                return new ImageIcon(newImg);
            } else {
                System.err.println("Warning: Ikon tidak ditemukan -> " + path);
                return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    private void styleButton(JButton btn, Color bgColor) {
        btn.setFont(new Font("Segoe UI", Font.BOLD, 16));
        btn.setBackground(bgColor);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorder(new EmptyBorder(10, 20, 10, 20));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setIconTextGap(15); // Jarak antara ikon dan teks
    }
    
    private void styleComboBox(JComboBox box) {
        box.setFont(UI_FONT);
        box.setPreferredSize(new Dimension(200, 35));
    }

    // --- LOGIKA UTAMA ---

    private void startSingleCaptureCountdown() {
        if (currentCaptureSlot >= maxPhotos) {
            JOptionPane.showMessageDialog(this, "Galeri sudah penuh. Silakan simpan strip foto Anda.");
            return;
        }
        btnCapture.setEnabled(false);
        countdownValue = 3;
        countdownPainter.setCountdownText(String.valueOf(countdownValue));
        webcamPanel.repaint();
        btnCapture.setText("SIAP...");
        countdownTimer = new Timer(1000, e -> {
            countdownValue--;
            countdownPainter.setCountdownText(String.valueOf(countdownValue));
            webcamPanel.repaint();
            if (countdownValue <= 0) {
                ((Timer)e.getSource()).stop();
                countdownPainter.setCountdownText("");
                webcamPanel.repaint();
                takeSinglePicture();
            }
        });
        countdownTimer.start();
    }

    private void takeSinglePicture() {
        playSound("shutter.wav");
        BufferedImage rawImage = service.captureImage();
        if (rawImage == null) {
            btnCapture.setEnabled(true);
            return;
        }
        String selectedFilterName = (String) comboFilter.getSelectedItem();
        FilterStrategy selectedFilter = filterStrategies.get(selectedFilterName);
        BufferedImage filteredImage = selectedFilter.applyFilter(rawImage);
        service.addCapturedImage(filteredImage);
        updateGallery();
        currentCaptureSlot++;
        if (currentCaptureSlot >= maxPhotos) {
            btnCapture.setEnabled(false);
            btnSave.setEnabled(true);
            btnCapture.setBackground(Color.DARK_GRAY); 
            btnCapture.setText("GALERI PENUH");
        } else {
            btnCapture.setEnabled(true);
            btnCapture.setText("AMBIL FOTO (" + (currentCaptureSlot + 1) + "/" + maxPhotos + ")");
        }
    }

    private void updateGallery() {
        int capturedCount = service.getCapturedImages().size();
        for (int i = 0; i < maxPhotos; i++) {
            if (i < capturedCount) {
                BufferedImage img = service.getCapturedImages().get(i);
                Image thumbnail = img.getScaledInstance(180, 135, Image.SCALE_SMOOTH);
                galleryLabels[i].setIcon(new ImageIcon(thumbnail));
                galleryLabels[i].setText(null);
                galleryLabels[i].setBorder(BorderFactory.createLineBorder(Color.WHITE, 2));
            } else {
                galleryLabels[i].setIcon(null);
                galleryLabels[i].setText("Slot " + (i+1));
                galleryLabels[i].setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Color.DARK_GRAY, 1),
                    BorderFactory.createMatteBorder(5, 5, 20, 5, new Color(40, 40, 40))
                ));
            }
        }
    }
    
    private void saveStripProcess() {
        try {
            BufferedImage finalStrip = service.generateStrip(this.selectedTemplateId);

            ImageIcon previewIcon = new ImageIcon(finalStrip.getScaledInstance(
                finalStrip.getWidth() / 2, finalStrip.getHeight() / 2, Image.SCALE_SMOOTH));
            
            int choice = JOptionPane.showConfirmDialog(
                this, new JLabel(previewIcon), "Preview Hasil",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            
            if (choice != JOptionPane.OK_OPTION) return;

            ExportStrategy strategy;
            String selectedExport = (String) comboExport.getSelectedItem();
            if (selectedExport.equals("Google Drive")) {
                strategy = new DriveExportStrategy();
            } else {
                strategy = new LocalExportStrategy();
            }

            service.saveFinalImage(strategy, finalStrip);
            
            JOptionPane.showMessageDialog(this, "Berhasil disimpan!");
            
            service.clearCapturedImages();
            updateGallery();
            btnSave.setEnabled(false);
            btnCapture.setEnabled(true);
            btnCapture.setBackground(PRIMARY_COLOR); 
            currentCaptureSlot = 0;
            btnCapture.setText("AMBIL FOTO (1/" + maxPhotos + ")");

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void playSound(String soundFileName) {
        if (soundFileName.toLowerCase().endsWith(".wav")) {
            try {
                File soundFile = new File(soundFileName);
                if (soundFile.exists()) {
                    AudioInputStream audioIn = AudioSystem.getAudioInputStream(soundFile.toURI().toURL());
                    Clip clip = AudioSystem.getClip();
                    clip.open(audioIn);
                    clip.start();
                }
            } catch (Exception e) {
                System.err.println("Error audio: " + e.getMessage());
            }
        }
    }
    
    private class CountdownPainter implements WebcamPanel.Painter {
        private Painter defaultPainter;
        private String countdownText = "";
        private Font countdownFont;
        public CountdownPainter(Painter defaultPainter) {
            this.defaultPainter = defaultPainter;
            this.countdownFont = new Font("Segoe UI", Font.BOLD, 150);
        }
        public void setCountdownText(String text) { this.countdownText = text; }
        
        @Override
        public void paintPanel(WebcamPanel panel, Graphics2D g2) {
            defaultPainter.paintPanel(panel, g2);
        }
        @Override
        public void paintImage(WebcamPanel panel, BufferedImage image, Graphics2D g2) {
            defaultPainter.paintImage(panel, image, g2);
            if (countdownText.isEmpty()) return;
            
            g2.setColor(new Color(0, 0, 0, 100));
            g2.fillRect(0, 0, panel.getWidth(), panel.getHeight());
            
            g2.setFont(countdownFont);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            FontMetrics metrics = g2.getFontMetrics(countdownFont);
            int x = (panel.getWidth() - metrics.stringWidth(countdownText)) / 2;
            int y = (panel.getHeight() - metrics.getHeight()) / 2 + metrics.getAscent();
            
            g2.setColor(new Color(0, 0, 0, 150));
            g2.drawString(countdownText, x + 5, y + 5);
            g2.setColor(new Color(255, 255, 255));
            g2.drawString(countdownText, x, y);
        }
    }
}