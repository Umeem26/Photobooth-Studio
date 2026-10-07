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
}
