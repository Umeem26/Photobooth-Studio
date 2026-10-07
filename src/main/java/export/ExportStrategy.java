package export;

import java.awt.image.BufferedImage;
import java.io.File;

public interface ExportStrategy {
    // Parameter ke-2 ditambahkan: File videoFile
    boolean export(BufferedImage image, File videoFile);
    
    String getStrategyName();
}