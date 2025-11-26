package template;

import model.StripTemplate;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.awt.Color;

public class TemplateHorizontal implements StripTemplate {

    private int photoCount;

    public TemplateHorizontal(int photoCount) {
        this.photoCount = photoCount;
    }

    public TemplateHorizontal() {
        this(4);
    }

    @Override
    public String getTemplateName() {
        return "Strip Horizontal (" + photoCount + " Foto)";
    }

    @Override
    public String getTemplateId() {
        return "TPL-H-" + photoCount;
    }

    @Override
    public int getMaxPhotos() {
        return photoCount;
    }

    @Override
    public BufferedImage applyTemplate(ArrayList<BufferedImage> images) {
        System.out.println("LOG: Menerapkan Template Horizontal (" + photoCount + " foto)...");
        
        int photoWidth = 200;
        int photoHeight = 150;
        int padding = 10;
        
        int stripWidth = (photoCount * photoWidth) + ((photoCount + 1) * padding);
        int stripHeight = photoHeight + (2 * padding);

        BufferedImage finalStrip = new BufferedImage(stripWidth, stripHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = finalStrip.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, stripWidth, stripHeight);

        int currentX = padding;
        for (int i = 0; i < photoCount && i < images.size(); i++) {
            BufferedImage originalImage = images.get(i);
            g.drawImage(cropImage(originalImage, photoWidth, photoHeight), currentX, padding, photoWidth, photoHeight,
                    null);
            currentX += photoWidth + padding;
        }

        g.dispose();
        return finalStrip;
    }

    @Override
    public BufferedImage getPreviewImage() {
        int w = 430;
        int h = 95;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.setColor(Color.DARK_GRAY);

        int photoWidth = 100;
        int photoHeight = 75;
        int padding = 6;
        int currentX = padding;

        for (int i = 0; i < photoCount; i++) {
            g.fillRect(currentX, padding, photoWidth, photoHeight);
            currentX += photoWidth + padding;
        }
        
        g.dispose();
        return img;
    }

    @Override
    public int getPhotoCount() {
        return 4; // Template ini butuh 3 foto
    }
}