package template;

import model.StripTemplate;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;

public class TemplateFourCorners implements StripTemplate {

    @Override
    public String getTemplateName() { return "4 Pojok (Logo di Tengah)"; }
    @Override
    public String getTemplateId() { return "TPL-COR"; }

    @Override
    public BufferedImage applyTemplate(ArrayList<BufferedImage> images) {
        System.out.println("LOG: Menerapkan Template 4 Pojok...");
        
        int canvasSize = 400;
        int photoSize = 150;
        int padding = 10;
        int innerSpace = canvasSize - (2 * photoSize) - (2 * padding); 

        BufferedImage finalStrip = new BufferedImage(canvasSize, canvasSize, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = finalStrip.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, canvasSize, canvasSize);

        if (images.size() > 0)
            g.drawImage(images.get(0), padding, padding, photoSize, photoSize, null);
        if (images.size() > 1)
            g.drawImage(images.get(1), padding + photoSize + innerSpace, padding, photoSize, photoSize, null);
        if (images.size() > 2)
            g.drawImage(images.get(2), padding, padding + photoSize + innerSpace, photoSize, photoSize, null);
        if (images.size() > 3)
            g.drawImage(images.get(3), padding + photoSize + innerSpace, padding + photoSize + innerSpace, photoSize, photoSize, null);

        g.setColor(Color.BLACK);
        g.setFont(new Font("Arial", Font.BOLD, 24));
        String text = "PHOTOBOOTH";
        FontMetrics fm = g.getFontMetrics();
        int textX = (canvasSize - fm.stringWidth(text)) / 2;
        int textY = (canvasSize / 2) + (fm.getAscent() / 2);
        g.drawString(text, textX, textY);
            
        g.dispose();
        return finalStrip;
    }

    @Override
    public BufferedImage getPreviewImage() {
        int w = 210;
        int h = 210;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.setColor(Color.DARK_GRAY);

        int photoSize = 80;
        int padding = 10;
        int innerSpace = w - (2 * photoSize) - (2 * padding); 

        g.fillRect(padding, padding, photoSize, photoSize);
        g.fillRect(padding + photoSize + innerSpace, padding, photoSize, photoSize);
        g.fillRect(padding, padding + photoSize + innerSpace, photoSize, photoSize);
        g.fillRect(padding + photoSize + innerSpace, padding + photoSize + innerSpace, photoSize, photoSize);
        
        g.dispose();
        return img;
    }
}