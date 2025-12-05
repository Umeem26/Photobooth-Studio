package test;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import service.PhotoboothService;
import exception.TemplateNotFoundException;
import java.awt.image.BufferedImage;

public class PhotoboothServiceTest {

    private PhotoboothService service;

    @BeforeEach
    void setUp() {
        service = new PhotoboothService();
        service.clearCapturedImages();
    }

    @Test
    @DisplayName("Fitur Add Image harus menambah jumlah foto di list")
    void testAddCapturedImage() {
        BufferedImage dummyImage = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        
        service.addCapturedImage(dummyImage);
        
        assertEquals(1, service.getCapturedImages().size(), "Jumlah foto harusnya 1");
        
        service.addCapturedImage(dummyImage);
        assertEquals(2, service.getCapturedImages().size(), "Jumlah foto harusnya 2");
    }

    @Test
    @DisplayName("Fitur Clear harus mengosongkan list foto")
    void testClearImages() {
        BufferedImage dummyImage = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        service.addCapturedImage(dummyImage);
        service.addCapturedImage(dummyImage);
        
        assertFalse(service.getCapturedImages().isEmpty());
        
        service.clearCapturedImages();
        
        assertTrue(service.getCapturedImages().isEmpty(), "List foto harus kosong setelah di-clear");
        assertEquals(0, service.getCapturedImages().size());
    }

    @Test
    @DisplayName("Harus melempar Exception jika Template ID tidak valid")
    void testGenerateStripWithInvalidId() {
        String invalidId = "TPL-Gaib";

        Exception exception = assertThrows(TemplateNotFoundException.class, () -> {
            service.generateStrip(invalidId);
        });

        assertTrue(exception.getMessage().contains("Template tidak ditemukan"));
    }
}