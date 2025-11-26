package model;

import java.awt.image.BufferedImage;
import java.util.ArrayList;

public interface StripTemplate {
    
    BufferedImage applyTemplate(ArrayList<BufferedImage> images);
    
    String getTemplateName();
    
    String getTemplateId();

    /**
     * Menghasilkan gambar pratinjau kecil untuk layout template.
     * 
     * @return BufferedImage pratinjau
     */
    BufferedImage getPreviewImage();

    // --- TAMBAHAN BARU ---
    /**
     * Mengembalikan jumlah foto yang dibutuhkan oleh template ini.
     */
    int getPhotoCount();
    // --- BATAS TAMBAHAN BARU ---

    int getMaxPhotos();

    default BufferedImage cropImage(BufferedImage originalImage, int targetWidth, int targetHeight) {
        double originalRatio = (double) originalImage.getWidth() / originalImage.getHeight();
        double targetRatio = (double) targetWidth / targetHeight;

        int w, h;
        if (originalRatio > targetRatio) {
            h = originalImage.getHeight();
            w = (int) (h * targetRatio);
        } else {
            w = originalImage.getWidth();
            h = (int) (w / targetRatio);
        }

        int x = (originalImage.getWidth() - w) / 2;
        int y = (originalImage.getHeight() - h) / 2;

        return originalImage.getSubimage(x, y, w, h);
    }
}