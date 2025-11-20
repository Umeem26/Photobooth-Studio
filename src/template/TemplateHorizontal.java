package template;

import model.StripTemplate;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.awt.Color;

public class TemplateHorizontal implements StripTemplate {

    @Override
    public String getTemplateName() { return "Strip Horizontal (4 Foto)"; }
    @Override
    public String getTemplateId() { return "TPL-H"; }

    @Override
    public BufferedImage applyTemplate(ArrayList<BufferedImage> images) {
        System.out.println("LOG: Menerapkan Template Horizontal...");
        
        int photoWidth = 200;
        int photoHeight = 150;
        int padding = 10;
        
        int stripWidth = (4 * photoWidth) + (5 * padding);
        int stripHeight = photoHeight + (2 * padding);

        BufferedImage finalStrip = new BufferedImage(stripWidth, stripHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = finalStrip.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, stripWidth, stripHeight);

        int currentX = padding;
        for (int i = 0; i < 4 && i < images.size(); i++) {
            BufferedImage originalImage = images.get(i);
            g.drawImage(originalImage, currentX, padding, photoWidth, photoHeight, null);
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

        for (int i = 0; i < 4; i++) {
            g.fillRect(currentX, padding, photoWidth, photoHeight);
            currentX += photoWidth + padding;
        }
        
        g.dispose();
        return img;
    }
}