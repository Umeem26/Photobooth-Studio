package template;

import model.StripTemplate;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;

public class TemplateFeatured implements StripTemplate {

    @Override
    public String getTemplateName() {
        return "Foto Utama (1 Besar, 3 Kecil)";
    }
    
    @Override
    public String getTemplateId() {
        return "TPL-F";
    }
    
    @Override
    public int getMaxPhotos() {
        return 4;
    }
        
    @Override
    public BufferedImage applyTemplate(ArrayList<BufferedImage> images) {
        System.out.println("LOG: Menerapkan Template Foto Utama...");
        
        int padding = 10;
        int bigWidth = 300;
        int bigHeight = 400;
        int smallWidth = 100;
        int smallHeight = (bigHeight - (2 * padding)) / 3;
        
        int totalWidth = padding + bigWidth + padding + smallWidth + padding;
        int totalHeight = padding + bigHeight + padding;

        BufferedImage finalStrip = new BufferedImage(totalWidth, totalHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = finalStrip.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, totalWidth, totalHeight);

        if (images.size() > 0)
            g.drawImage(cropImage(images.get(0), bigWidth, bigHeight), padding, padding, bigWidth, bigHeight, null);

        int currentY = padding;
        int smallX = padding + bigWidth + padding;
        
        if (images.size() > 1)
            g.drawImage(cropImage(images.get(1), smallWidth, smallHeight), smallX, currentY, smallWidth, smallHeight,
                    null);
        currentY += smallHeight + padding;
        
        if (images.size() > 2)
            g.drawImage(cropImage(images.get(2), smallWidth, smallHeight), smallX, currentY, smallWidth, smallHeight,
                    null);
        
        currentY += smallHeight + padding;

        if (images.size() > 3)
            g.drawImage(cropImage(images.get(3), smallWidth, smallHeight), smallX, currentY, smallWidth, smallHeight,
                    null);
        
        g.dispose();
        return finalStrip;
    }

    @Override
    public BufferedImage getPreviewImage() {
        int w = 215;
        int h = 210;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.setColor(Color.DARK_GRAY);

        int padding = 5;
        int bigW = 150;
        int bigH = 200;
        int smallW = 50;
        int smallH = (bigH - (2 * padding)) / 3;

        g.fillRect(padding, padding, bigW, bigH);
        
        int currentY = padding;
        int smallX = padding + bigW + padding;
        
        g.fillRect(smallX, currentY, smallW, smallH);
        currentY += smallH + padding;
        g.fillRect(smallX, currentY, smallW, smallH);
        currentY += smallH + padding;
        g.fillRect(smallX, currentY, smallW, smallH);
        
        g.dispose();
        return img;
    }

    @Override
    public int getPhotoCount() {
        return 4; // Template ini butuh 3 foto
    }
}