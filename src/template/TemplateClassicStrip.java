package template;

import model.StripTemplate;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;

public class TemplateClassicStrip implements StripTemplate {

    @Override
    public String getTemplateName() { return "Strip Klasik (3 Foto + Logo)"; }
    @Override
    public String getTemplateId() { return "TPL-C"; }

    @Override
    public BufferedImage applyTemplate(ArrayList<BufferedImage> images) {
        System.out.println("LOG: Menerapkan Template Strip Klasik...");
        int stripWidth = 200;
        int photoHeight = 150;
        int padding = 10;
        int logoHeight = 100;
        int stripHeight = (3 * photoHeight) + logoHeight + (5 * padding);
        BufferedImage finalStrip = new BufferedImage(stripWidth, stripHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = finalStrip.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, stripWidth, stripHeight);
        int currentY = padding;
        for (int i = 0; i < 3 && i < images.size(); i++) {
            BufferedImage originalImage = images.get(i);
            g.drawImage(originalImage, padding, currentY, stripWidth - (2 * padding), photoHeight, null);
            currentY += photoHeight + padding;
        }
        g.setColor(Color.BLACK);
        g.fillRect(padding, currentY, stripWidth - (2 * padding), logoHeight);
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 18));
        String text = "Photobooth Keren";
        FontMetrics fm = g.getFontMetrics();
        int textX = (stripWidth - fm.stringWidth(text)) / 2;
        int textY = currentY + (logoHeight / 2) + fm.getAscent() / 2;
        g.drawString(text, textX, textY);
        g.dispose();
        return finalStrip;
    }

    @Override
    public BufferedImage getPreviewImage() {
        int w = 100;
        int h = 300; 
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.setColor(Color.DARK_GRAY);

        int padding = 5;
        int photoHeight = (h / 4);
        int currentY = padding;
        
        for (int i = 0; i < 3; i++) {
            g.fillRect(padding, currentY, w - (2 * padding), photoHeight);
            currentY += photoHeight + padding;
        }
        
        g.setColor(Color.BLACK);
        int logoHeight = h - currentY - padding;
        g.fillRect(padding, currentY, w - (2 * padding), logoHeight);
        
        g.dispose();
        return img;
    }
}