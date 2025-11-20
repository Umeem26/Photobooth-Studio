package template;

import model.StripTemplate;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.awt.Color;

public class TemplateVertical implements StripTemplate {

    public TemplateVertical() { }

    @Override
    public String getTemplateName() { return "Strip Vertikal (4 Foto)"; }
    @Override
    public String getTemplateId() { return "TPL-V"; }

    @Override
    public BufferedImage applyTemplate(ArrayList<BufferedImage> images) {
        System.out.println("LOG: Menerapkan Template Vertikal...");
        int stripWidth = 200;
        int photoHeight = 150;
        int padding = 10;
        int stripHeight = (4 * photoHeight) + (5 * padding);
        BufferedImage finalStrip = new BufferedImage(stripWidth, stripHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = finalStrip.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, stripWidth, stripHeight);
        int currentY = padding;
        for (int i = 0; i < 4 && i < images.size(); i++) {
            BufferedImage originalImage = images.get(i);
            g.drawImage(originalImage, padding, currentY, stripWidth - (2 * padding), photoHeight, null);
            currentY += photoHeight + padding;
        }
        g.dispose();
        return finalStrip;
    }

    @Override
    public BufferedImage getPreviewImage() {
        int w = 100;
        int h = 325;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.setColor(Color.DARK_GRAY); 
        
        int padding = 5;
        int photoHeight = 75; 
        int currentY = padding;
        
        for (int i = 0; i < 4; i++) {
            g.fillRect(padding, currentY, w - (2 * padding), photoHeight);
            currentY += photoHeight + padding;
        }
        
        g.dispose();
        return img;
    }
}