package template;

import model.StripTemplate;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.awt.Color;

public class TemplateVertical implements StripTemplate {

    private int photoCount;

    public TemplateVertical(int photoCount) {
        this.photoCount = photoCount;
    }

    // Default constructor for backward compatibility or default usage
    public TemplateVertical() {
        this(4);
    }

    @Override
    public String getTemplateName() {
        return "Strip Vertikal (" + photoCount + " Foto)";
    }

    @Override
    public String getTemplateId() {
        return "TPL-V-" + photoCount;
    }

    @Override
    public int getMaxPhotos() {
        return photoCount;
    }

    @Override
    public BufferedImage applyTemplate(ArrayList<BufferedImage> images) {
        System.out.println("LOG: Menerapkan Template Vertikal (" + photoCount + " foto)...");
        int stripWidth = 200;
        int photoHeight = 150;
        int padding = 10;
        int stripHeight = (photoCount * photoHeight) + ((photoCount + 1) * padding);

        BufferedImage finalStrip = new BufferedImage(stripWidth, stripHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = finalStrip.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, stripWidth, stripHeight);

        int currentY = padding;
        for (int i = 0; i < photoCount && i < images.size(); i++) {
            BufferedImage originalImage = images.get(i);
            g.drawImage(cropImage(originalImage, stripWidth - (2 * padding), photoHeight), padding, currentY,
                    stripWidth - (2 * padding), photoHeight, null);
            currentY += photoHeight + padding;
        }
        g.dispose();
        return finalStrip;
    }

    @Override
    public BufferedImage getPreviewImage() {
        int w = 100;
        // Adjust height based on photo count for preview aspect ratio approximation
        int h = (photoCount * 75) + ((photoCount + 1) * 5);

        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.setColor(Color.DARK_GRAY);

        int padding = 5;
        int photoHeight = 75;
        int currentY = padding;

        for (int i = 0; i < photoCount; i++) {
            g.fillRect(padding, currentY, w - (2 * padding), photoHeight);
            currentY += photoHeight + padding;
        }

        g.dispose();
        return img;
    }

    @Override
    public int getPhotoCount() {
        return 4; // Template ini butuh 3 foto
    }
}