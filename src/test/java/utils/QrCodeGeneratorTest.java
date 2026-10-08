package utils;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

import utils.QrCodeGenerator;
import java.awt.image.BufferedImage;

public class QrCodeGeneratorTest {

    @Test
    void generatedQrDecodesBackToOriginalText() throws Exception {
        String text = "https://example.com/vandebooth/session/123";
        BufferedImage qr = QrCodeGenerator.generate(text, 300);

        assertEquals(300, qr.getWidth());
        assertEquals(300, qr.getHeight());

        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(qr)));
        assertEquals(text, new QRCodeReader().decode(bitmap).getText());
    }

    @Test
    void rejectsEmptyTextAndInvalidSize() {
        assertThrows(IllegalArgumentException.class, () -> QrCodeGenerator.generate("", 300));
        assertThrows(IllegalArgumentException.class, () -> QrCodeGenerator.generate(null, 300));
        assertThrows(IllegalArgumentException.class, () -> QrCodeGenerator.generate("x", 0));
    }

    @Test
    void smallMarginKeepsQrReadableAndFillsMoreOfTheImage() throws Exception {
        String text = "https://example.com/vandebooth/session/123";
        BufferedImage tight = QrCodeGenerator.generate(text, 300, 1);
        BufferedImage normal = QrCodeGenerator.generate(text, 300);
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(tight)));
        assertEquals(text, new QRCodeReader().decode(bitmap).getText());
        assertTrue(firstDark(tight) < firstDark(normal), "quiet zone lebih tipis");
        assertThrows(IllegalArgumentException.class, () -> QrCodeGenerator.generate(text, 300, -1));
    }

    private static int firstDark(BufferedImage img) {
        int y = img.getHeight() / 2;
        for (int x = 0; x < img.getWidth(); x++) {
            if ((img.getRGB(x, y) & 0xFFFFFF) == 0) return x;
        }
        return img.getWidth();
    }
}
