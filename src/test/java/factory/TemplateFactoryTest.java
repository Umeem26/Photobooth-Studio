package factory;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import factory.TemplateFactory;
import model.StripTemplate;
import template.TemplateVertical;
import template.TemplateHorizontal;

public class TemplateFactoryTest {

    private TemplateFactory factory;

    @BeforeEach
    void setUp() {
        factory = new TemplateFactory();
    }

    @Test
    @DisplayName("Factory harus membuat TemplateVertical saat diminta 'TPL-V'")
    void testCreateVerticalTemplate() {
        StripTemplate result = factory.createTemplate("TPL-V-4");

        assertNotNull(result, "Objek tidak boleh null");
        assertTrue(result instanceof TemplateVertical, "Objek harus berupa instance TemplateVertical");
        assertEquals("TPL-V-4", result.getTemplateId(), "ID Template harus sesuai");
        assertEquals(4, result.getPhotoCount(), "Template Vertical harus butuh 4 foto");
    }

    @Test
    @DisplayName("Factory harus membuat TemplateHorizontal saat diminta 'TPL-H'")
    void testCreateHorizontalTemplate() {
        StripTemplate result = factory.createTemplate("TPL-H-4");

        assertNotNull(result, "Objek tidak boleh null");
        assertTrue(result instanceof TemplateHorizontal, "Objek harus berupa instance TemplateHorizontal");
        assertEquals("TPL-H-4", result.getTemplateId(), "ID Template harus sesuai");
        assertEquals(4, result.getPhotoCount(), "Template Horizontal harus butuh 4 foto");
    }

    @Test
    @DisplayName("Factory harus mengembalikan null jika ID salah")
    void testInvalidTemplateId() {
        StripTemplate result = factory.createTemplate("TPL-Ngasal");
        assertNull(result, "Harusnya null jika ID tidak dikenali");
    }
}