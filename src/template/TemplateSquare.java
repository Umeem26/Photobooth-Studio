package template;

import model.StripTemplate;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.awt.Color;

public class TemplateSquare implements StripTemplate {

    @Override
    public String getTemplateName() { return "Kotak 2x2 (4 Foto)"; }
    @Override
    public String getTemplateId() { return "TPL-SQ"; }

    @Override
    public BufferedImage applyTemplate(ArrayList<BufferedImage> images) {
        System.out.println("LOG: Menerapkan Template Kotak 2x2...");
        int photoSize = 200;
        int padding = 10;
        int canvasSize = (2 * photoSize) + (3 * padding);
        BufferedImage finalStrip = new BufferedImage(canvasSize, canvasSize, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = finalStrip.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, canvasSize, canvasSize);
        if (images.size() > 0) 
            g.drawImage(images.get(0), padding, padding, photoSize, photoSize, null);
        if (images.size() > 1) 
            g.drawImage(images.get(1), photoSize + (2 * padding), padding, photoSize, photoSize, null);
        if (images.size() > 2) 
            g.drawImage(images.get(2), padding, photoSize + (2 * padding), photoSize, photoSize, null);
        if (images.size() > 3) 
            g.drawImage(images.get(3), photoSize + (2 * padding), photoSize + (2 * padding), photoSize, photoSize, null);
        g.dispose();
        return finalStrip;
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
}