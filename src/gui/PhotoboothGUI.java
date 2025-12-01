package gui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
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
import filter.VintageFilterStrategy;

import utils.VideoRecorder;
import utils.VideoPreviewWindow;
import utils.StripVideoPreviewWindow;


public class PhotoboothGUI extends JFrame {

    private PhotoboothService service;

    private WebcamPanel webcamPanel;
    private JPanel pnlGallery;
    private JButton btnCapture;
    private JButton btnSave;
    private JComboBox<String> comboExport;
    private JComboBox<String> comboFilter;
    
    private Map<String, FilterStrategy> filterStrategies;
    private JLabel[] galleryLabels;
    private Timer countdownTimer;
    private int countdownValue;
    private int currentCaptureSlot = 0;
    private CountdownPainter countdownPainter;

    private VideoRecorder videoRecorder = new VideoRecorder();
    private File[] videoFiles;
    private JButton btnPreviewVideo;
    private JButton btnRetake; 
    
    private String selectedTemplateId;
    private int maxPhotos;

    private JComboBox<String> comboCamera;
    private java.util.List<com.github.sarxos.webcam.Webcam> availableWebcams;
    private JPanel webcamContainer;

    private final Color PRIMARY_COLOR = new Color(0, 120, 215); 
    private final Color SUCCESS_COLOR = new Color(30, 160, 80); 
    private final Font TITLE_FONT = new Font("Segoe UI", Font.BOLD, 24);
    private final Font UI_FONT = new Font("Segoe UI", Font.PLAIN, 14);

    public PhotoboothGUI(PhotoboothService service, String selectedTemplateId) {
        this.service = service;
        this.selectedTemplateId = selectedTemplateId;

        StripTemplate template = service.getAvailableTemplates().get(selectedTemplateId);
        this.maxPhotos = (template != null) ? template.getMaxPhotos() : 4;
        videoFiles = new File[maxPhotos];

        setTitle("Photobooth Studio Pro");
        setSize(1100, 750);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));
        getContentPane().setBackground(new Color(30, 30, 30));

        initializeFilters();
        initCameraCombo();  

        add(createHeaderPanel(), BorderLayout.NORTH);

        webcamPanel = new WebcamPanel(service.getCameraManager().getWebcam());
        webcamPanel.setMirrored(false);
        Painter defaultPainter = webcamPanel.getPainter();
        countdownPainter = new CountdownPainter(defaultPainter);
        webcamPanel.setPainter(countdownPainter);

        webcamContainer = new JPanel(new BorderLayout());
        webcamContainer.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY, 2));
        webcamContainer.add(webcamPanel, BorderLayout.CENTER);
        add(webcamContainer, BorderLayout.CENTER);

        add(createActionPanel(), BorderLayout.SOUTH);

        // Panggil createGalleryPanel yang sekarang dinamis
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
        FilterStrategy vintageFilter = new VintageFilterStrategy();

        filterStrategies.put(noFilter.getFilterName(), noFilter);
        filterStrategies.put(grayFilter.getFilterName(), grayFilter);
        filterStrategies.put(vintageFilter.getFilterName(), vintageFilter);
    }

    private JPanel createHeaderPanel() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(45, 45, 48));
        header.setBorder(new EmptyBorder(15, 20, 15, 20));

        JLabel title = new JLabel("PHOTOBOOTH STUDIO");
        title.setFont(TITLE_FONT);
        title.setForeground(Color.WHITE);
        title.setIcon(loadIcon("camera.png", 32)); 
        title.setIconTextGap(15);

        JLabel subtitle = new JLabel("Template: " + this.selectedTemplateId);
        subtitle.setFont(new Font("Segoe UI", Font.ITALIC, 14));
        subtitle.setForeground(Color.LIGHT_GRAY);

        header.add(title, BorderLayout.WEST);
        header.add(subtitle, BorderLayout.EAST);
        return header;
    }
    
    // --- MODIFIKASI: Galeri Dinamis sesuai maxPhotos ---
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

    private void initCameraCombo() {
        availableWebcams = service.getCameraManager().getDetectedWebcams();

        java.util.List<String> names = new java.util.ArrayList<>();
        for (com.github.sarxos.webcam.Webcam cam : availableWebcams) {
            names.add(cam.getName());
        }

        comboCamera = new JComboBox<>(names.toArray(new String[0]));
        styleComboBox(comboCamera);

        // Set pilihan awal ke kamera yang sedang dipakai sekarang
        com.github.sarxos.webcam.Webcam current = service.getCameraManager().getWebcam();
        // tapi kita sudah punya langsung:
        current = service.getCameraManager().getWebcam();

        int idx = availableWebcams.indexOf(current);
        if (idx >= 0) {
            comboCamera.setSelectedIndex(idx);
        }

        // Listener ketika user mengganti kamera
        comboCamera.addActionListener(e -> {
            int i = comboCamera.getSelectedIndex();
            if (i < 0 || i >= availableWebcams.size()) return;

            com.github.sarxos.webcam.Webcam selectedCam = availableWebcams.get(i);

            // ganti di CameraManager (supaya logic lain yang pakai CameraManager ikut kamera ini)
            service.getCameraManager().switchToWebcam(selectedCam);

            // Bangun WebcamPanel baru dengan kamera terpilih
            webcamPanel = new WebcamPanel(selectedCam);
            webcamPanel.setMirrored(false);

            Painter defaultPainter = webcamPanel.getPainter();
            countdownPainter = new CountdownPainter(defaultPainter);
            webcamPanel.setPainter(countdownPainter);

            // Ganti komponen di container
            if (webcamContainer != null) {
                webcamContainer.removeAll();
                webcamContainer.add(webcamPanel, BorderLayout.CENTER);
                webcamContainer.revalidate();
                webcamContainer.repaint();
            }
            webcamPanel.start();
        });
    }

    private JPanel createActionPanel() {
        JPanel mainActionPanel = new JPanel(new BorderLayout(10, 10));
        mainActionPanel.setBackground(new Color(30, 30, 30));
        mainActionPanel.setBorder(new EmptyBorder(10, 20, 20, 20));

        // =======================
        //  TOMBOL UTAMA (ATAS)
        // =======================

        // Tombol AMBIL FOTO
        btnCapture = new JButton("AMBIL FOTO (1/" + maxPhotos + ")");
        styleButton(btnCapture, PRIMARY_COLOR);
        btnCapture.setIcon(loadIcon("camera.png", 24));
        btnCapture.addActionListener(e -> startSingleCaptureCountdown());

        // Tombol SIMPAN STRIP
        btnSave = new JButton("SIMPAN STRIP");
        styleButton(btnSave, SUCCESS_COLOR);
        btnSave.setEnabled(false);
        btnSave.setIcon(loadIcon("save.png", 24));
        btnSave.addActionListener(e -> saveStripProcess());

        // Tombol PREVIEW VIDEO
        btnPreviewVideo = new JButton("PREVIEW VIDEO");
        styleButton(btnPreviewVideo, new Color(70, 70, 70));
        btnPreviewVideo.setEnabled(false);
        btnPreviewVideo.addActionListener(e -> {
            java.util.List<String> labels = new java.util.ArrayList<>();
            java.util.List<File> files = new java.util.ArrayList<>();

            // Kumpulkan semua video per sesi
            for (int i = 0; i < maxPhotos; i++) {
                if (videoFiles != null && videoFiles[i] != null && videoFiles[i].exists()) {
                    labels.add("Video sesi " + (i + 1));
                    files.add(videoFiles[i]);
                }
            }

            // cek apakah semua slot punya video → baru tampilkan opsi strip
            boolean hasFullStrip = true;
            if (videoFiles == null) {
                hasFullStrip = false;
            } else {
                for (int i = 0; i < maxPhotos; i++) {
                    if (i >= videoFiles.length ||
                        videoFiles[i] == null ||
                        !videoFiles[i].exists()) {
                        hasFullStrip = false;
                        break;
                    }
                }
            }

            final String STRIP_LABEL = "Video strip (semua sesi)";
            if (hasFullStrip && maxPhotos >= 2) {
                labels.add(STRIP_LABEL);
                // (tidak ditambah ke files, diproses khusus)
            }

            if (labels.isEmpty()) {
                JOptionPane.showMessageDialog(
                        this,
                        "Belum ada video rekaman untuk sesi ini.",
                        "Info",
                        JOptionPane.INFORMATION_MESSAGE
                );
                return;
            }

            Object choice = JOptionPane.showInputDialog(
                    this,
                    "Pilih video yang ingin dipreview:",
                    "Preview Video",
                    JOptionPane.PLAIN_MESSAGE,
                    null,
                    labels.toArray(),
                    labels.get(0)
            );

            if (choice != null) {
                String chosen = choice.toString();
                if (chosen.equals(STRIP_LABEL)) {
                    // pakai template yang sama dengan cetak
                    StripTemplate tpl = service.getAvailableTemplates().get(selectedTemplateId);
                    if (tpl != null) {
                        new StripVideoPreviewWindow(videoFiles, maxPhotos, tpl);
                    } else {
                        JOptionPane.showMessageDialog(
                                this,
                                "Template video tidak ditemukan.",
                                "Error",
                                JOptionPane.ERROR_MESSAGE
                        );
                    }
                } else {
                    int idx = labels.indexOf(chosen);
                    if (idx >= 0 && idx < files.size()) {
                        new VideoPreviewWindow(files.get(idx));
                    }
                }
            }
        });

        // Tombol AMBIL ULANG (RETAKE)
        btnRetake = new JButton("AMBIL ULANG FOTO");
        styleButton(btnRetake, new Color(90, 90, 90));
        btnRetake.setEnabled(false);
        btnRetake.addActionListener(e -> retakeLastPhoto());

        // Panel tombol baris atas
        JPanel buttonPanel = new JPanel(new GridLayout(1, 2, 20, 0));
        buttonPanel.setOpaque(false);
        buttonPanel.add(btnCapture);
        buttonPanel.add(btnSave);
        buttonPanel.add(btnPreviewVideo);
        buttonPanel.add(btnRetake);
        comboFilter.addActionListener(e -> {
            String selected = (String) comboFilter.getSelectedItem();
            FilterStrategy strategy = filterStrategies.get(selected);
            countdownPainter.setFilter(strategy);
        });

        // =======================
        //  DROPDOWN (BAWAH)
        // =======================

        // Combo filter (sudah diisi dari filterStrategies)
        comboFilter = new JComboBox<>();
        for (String filterName : filterStrategies.keySet()) {
            comboFilter.addItem(filterName);
        }
        styleComboBox(comboFilter);

        // Combo export
        comboExport = new JComboBox<>(new String[]{"Komputer", "Google Drive"});
        styleComboBox(comboExport);

        // comboCamera DIASUMSIKAN sudah dibuat & di-style di tempat lain
        // (misalnya di initCameraCombo())
        // styleComboBox(comboCamera);  // kalau belum di-style, boleh diaktifkan

        JPanel optionsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 40, 0));
        optionsPanel.setOpaque(false);

        // Filter
        JPanel filterP = new JPanel(new BorderLayout(5, 0));
        filterP.setOpaque(false);
        JLabel lblFilter = new JLabel("Filter Efek:");
        lblFilter.setFont(UI_FONT);
        lblFilter.setForeground(Color.WHITE);
        filterP.add(lblFilter, BorderLayout.NORTH);
        filterP.add(comboFilter, BorderLayout.CENTER);

        // Export
        JPanel exportP = new JPanel(new BorderLayout(5, 0));
        exportP.setOpaque(false);
        JLabel lblExport = new JLabel("Simpan Ke:");
        lblExport.setFont(UI_FONT);
        lblExport.setForeground(Color.WHITE);
        exportP.add(lblExport, BorderLayout.NORTH);
        exportP.add(comboExport, BorderLayout.CENTER);

        // Kamera
        JPanel cameraP = new JPanel(new BorderLayout(5, 0));
        cameraP.setOpaque(false);
        JLabel lblCamera = new JLabel("Kamera:");
        lblCamera.setFont(UI_FONT);
        lblCamera.setForeground(Color.WHITE);
        cameraP.add(lblCamera, BorderLayout.NORTH);
        cameraP.add(comboCamera, BorderLayout.CENTER);

        optionsPanel.add(filterP);
        optionsPanel.add(exportP);
        optionsPanel.add(cameraP);

        // ====== RANGKAI PANEL ======
        mainActionPanel.add(buttonPanel, BorderLayout.NORTH);
        mainActionPanel.add(optionsPanel, BorderLayout.CENTER);

        return mainActionPanel;
    }

    private ImageIcon loadIcon(String path, int size) {
        try {
            File imgFile = new File(path);
            if (imgFile.exists()) {
                ImageIcon originalIcon = new ImageIcon(path);
                Image img = originalIcon.getImage();
                Image newImg = img.getScaledInstance(size, size, Image.SCALE_SMOOTH);
                return new ImageIcon(newImg);
            } else {
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
        btn.setIconTextGap(15);
    }
    
    private void styleComboBox(JComboBox box) {
        box.setFont(UI_FONT);
        box.setPreferredSize(new Dimension(200, 35));
    }

    // --- LOGIKA UTAMA (Disesuaikan dengan maxPhotos) ---

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

        // MULAI REKAMAN VIDEO
        videoRecorder.startRecording();

        countdownTimer = new Timer(1000, e -> {
            countdownValue--;
            countdownPainter.setCountdownText(String.valueOf(countdownValue));
            webcamPanel.repaint();

            if (countdownValue <= 0) {
                ((Timer) e.getSource()).stop();
                countdownPainter.setCountdownText("");
                webcamPanel.repaint();

                // STOP REKAMAN & TAMPILKAN PREVIEW
                File videoFile = videoRecorder.stopRecording();
                if (videoFile != null && currentCaptureSlot >= 0 && currentCaptureSlot < maxPhotos) {
                    videoFiles[currentCaptureSlot] = videoFile;   // simpan sesuai sesi foto sekarang
                }

                takeSinglePicture();
            }
        });

        countdownTimer.start();
    }

    private void takeSinglePicture() {
        // Ambil gambar dari kamera
        BufferedImage rawImage = service.captureImage();
        if (rawImage == null) {
            JOptionPane.showMessageDialog(this,
                    "Gagal mengambil gambar dari kamera.",
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
            // kalau gagal, izinkan user mencoba lagi
            btnCapture.setEnabled(true);
            btnCapture.setText("AMBIL FOTO (" + (currentCaptureSlot + 1) + "/" + maxPhotos + ")");
            return;
        }

        // Terapkan filter
        String selectedFilterName = (String) comboFilter.getSelectedItem();
        FilterStrategy strategy = filterStrategies.getOrDefault(selectedFilterName, new NoFilterStrategy());
        BufferedImage filtered = strategy.applyFilter(rawImage);

        // Simpan ke service (list capturedImages)
        service.addCapturedImage(filtered);

        // Update gallery
        updateGallery();

        // Naikkan slot saat ini
        currentCaptureSlot++;

        if (currentCaptureSlot < maxPhotos) {
            // Masih ada slot kosong → siap untuk sesi berikutnya (manual)
            btnCapture.setText("AMBIL FOTO (" + (currentCaptureSlot + 1) + "/" + maxPhotos + ")");
            btnCapture.setEnabled(true);

            // Sekarang sudah ada minimal 1 foto → boleh ambil ulang
            btnRetake.setEnabled(true);

        } else {
            // Sudah cukup foto (galeri penuh)
            btnCapture.setText("GALERI PENUH");
            btnCapture.setEnabled(false);
            btnSave.setEnabled(true);

            // Ada minimal 1 video rekaman?
            boolean hasAnyVideo = false;
            if (videoFiles != null) {
                for (int i = 0; i < maxPhotos; i++) {
                    if (videoFiles[i] != null && videoFiles[i].exists()) {
                        hasAnyVideo = true;
                        break;
                    }
                }
            }

            if (hasAnyVideo) {
                btnPreviewVideo.setEnabled(true);
            }

            // Tetap boleh ambil ulang foto terakhir (misal gak puas dengan 4/4)
            btnRetake.setEnabled(true);
        }
    }

    private void retakeLastPhoto() {
        // Kalau belum ada foto sama sekali, gak ada yang bisa diulang
        if (currentCaptureSlot <= 0) {
            JOptionPane.showMessageDialog(
                    this,
                    "Belum ada foto yang bisa diulang.",
                    "Info",
                    JOptionPane.INFORMATION_MESSAGE
            );
            return;
        }

        // Slot terakhir yang sudah terisi (0-based index)
        int slotToRetake = currentCaptureSlot - 1;

        // Hapus foto terakhir dari service
        java.util.List<BufferedImage> list = service.getCapturedImages();
        if (slotToRetake < list.size()) {
            list.remove(slotToRetake);
        }

        // Hapus video terakhir untuk slot itu
        if (videoFiles != null && slotToRetake < videoFiles.length) {
            File f = videoFiles[slotToRetake];
            videoFiles[slotToRetake] = null;
            // Kalau mau sekalian hapus file fisiknya:
            // if (f != null && f.exists()) f.delete();
        }

        // Set kita kembali ke slot itu
        currentCaptureSlot = slotToRetake;

        // Refresh galeri supaya slot itu jadi kosong lagi
        updateGallery();

        // Karena jumlah foto berkurang, jangan izinkan save dulu
        btnSave.setEnabled(false);

        // Preview video juga sembunyikan dulu (supaya tidak bingung)
        btnPreviewVideo.setEnabled(false);

        // Update teks tombol capture ke slot ini
        btnCapture.setText("AMBIL FOTO (" + (currentCaptureSlot + 1) + "/" + maxPhotos + ")");
        btnCapture.setEnabled(false); // akan di-enable lagi setelah countdown

        // Mulai countdown lagi untuk slot ini
        startSingleCaptureCountdown();
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
            
            btnPreviewVideo.setEnabled(false);
            java.util.Arrays.fill(videoFiles, null);

            btnRetake.setEnabled(false);

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
        private FilterStrategy currentFilter;

        public CountdownPainter(Painter defaultPainter) {
            this.defaultPainter = defaultPainter;
            this.countdownFont = new Font("Segoe UI", Font.BOLD, 150);
            this.currentFilter = new NoFilterStrategy();
        }

        public void setCountdownText(String text) {
            this.countdownText = text;
        }

        public void setFilter(FilterStrategy filter) {
            this.currentFilter = filter;
        }

        @Override
        public void paintPanel(WebcamPanel panel, Graphics2D g2) {
            defaultPainter.paintPanel(panel, g2);
        }

        @Override
        public void paintImage(WebcamPanel panel, BufferedImage image, Graphics2D g2) {

            // kalau sedang merekam, tambahkan frame ke video
            if (videoRecorder != null && videoRecorder.isRecording()) {
                videoRecorder.addFrame(image);
            }

            defaultPainter.paintImage(panel, image, g2);

            if (countdownText == null || countdownText.isEmpty()) return;

            g2.setColor(new Color(0, 0, 0, 150));
            BufferedImage processedImage = image;
            if (currentFilter != null) {
                processedImage = currentFilter.applyFilter(image);
            }
            defaultPainter.paintImage(panel, processedImage, g2);

            if (countdownText.isEmpty())
                return;

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