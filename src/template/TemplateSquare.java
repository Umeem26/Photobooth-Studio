package template;

import model.StripTemplate;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.awt.Color;

public class TemplateSquare implements StripTemplate {

    @Override
    public String getTemplateName() {
        return "Kotak 2x2 (4 Foto)";
    }

    @Override
    public String getTemplateId() {
        return "TPL-SQ";
    }

    @Override
    public int getMaxPhotos() {
        return 4;
    }

    @Override
    public BufferedImage applyTemplate(ArrayList<BufferedImage> images) {
        System.out.println("LOG: Menerapkan Template Kotak 2x2...");
        int w = 215;
        int h = 215;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);

        int photoSize = 100;
        int padding = 5;

        if (images.size() > 0) {
            g.drawImage(cropImage(images.get(0), photoSize, photoSize), padding, padding, photoSize, photoSize, null);
        }
        if (images.size() > 1) {
            g.drawImage(cropImage(images.get(1), photoSize, photoSize), photoSize + (2 * padding), padding, photoSize,
                    photoSize, null);
        }
        if (images.size() > 2) {
            g.drawImage(cropImage(images.get(2), photoSize, photoSize), padding, photoSize + (2 * padding), photoSize,
                    photoSize, null);
        }
        if (images.size() > 3) {
            g.drawImage(cropImage(images.get(3), photoSize, photoSize), photoSize + (2 * padding),
                    photoSize + (2 * padding), photoSize, photoSize, null);
        }

        g.dispose();
        return img;
    }

    @Override
    public BufferedImage getPreviewImage() {
        int w = 215;
        int h = 215;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.setColor(Color.DARK_GRAY);

        int photoSize = 100;
        int padding = 5;

        g.fillRect(padding, padding, photoSize, photoSize);
        g.fillRect(photoSize + (2 * padding), padding, photoSize, photoSize);
        g.fillRect(padding, photoSize + (2 * padding), photoSize, photoSize);
        g.fillRect(photoSize + (2 * padding), photoSize + (2 * padding), photoSize, photoSize);
        
        g.dispose();
        return img;
    }

    @Override
    public int getPhotoCount() {
        return 4; // Template ini butuh 3 foto
    }
}