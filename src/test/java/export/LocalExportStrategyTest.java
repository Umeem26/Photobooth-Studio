package export;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import javax.imageio.ImageIO;

class LocalExportStrategyTest {

    @TempDir
    Path tmp;

    @Test
    void writesPngToTargetAndCreatesParentFolders() throws Exception {
        File target = tmp.resolve("hasil").resolve("strip.png").toFile();
        LocalExportStrategy strategy = new LocalExportStrategy(target);

        assertTrue(strategy.export(new BufferedImage(30, 20, BufferedImage.TYPE_INT_RGB), null));

        BufferedImage written = ImageIO.read(target);
        assertEquals(30, written.getWidth());
        assertEquals(20, written.getHeight());
    }

    @Test
    void appendsPngExtensionWhenMissing() {
        File target = tmp.resolve("strip").toFile();
        assertEquals("strip.png", new LocalExportStrategy(target).getTarget().getName());
        assertEquals("A.PNG", new LocalExportStrategy(tmp.resolve("A.PNG").toFile()).getTarget().getName());
    }

    @Test
    void nullImageFailsWithoutWriting() {
        File target = tmp.resolve("kosong.png").toFile();
        assertFalse(new LocalExportStrategy(target).export(null, null));
        assertFalse(target.exists());
    }

    @Test
    void rejectsNullTarget() {
        assertThrows(IllegalArgumentException.class, () -> new LocalExportStrategy(null));
    }
}
