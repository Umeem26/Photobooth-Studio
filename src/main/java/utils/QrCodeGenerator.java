package utils;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.awt.image.BufferedImage;

/**
 * Util pembuat gambar QR Code (ZXing). Tidak bergantung pada GUI.
 */
public final class QrCodeGenerator {

    private QrCodeGenerator() {
    }

    public static BufferedImage generate(String text, int size) throws WriterException {
        if (text == null || text.isEmpty()) {
            throw new IllegalArgumentException("Teks QR tidak boleh kosong");
        }
        if (size <= 0) {
            throw new IllegalArgumentException("Ukuran QR harus positif");
        }
        BitMatrix bitMatrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size);
        return MatrixToImageWriter.toBufferedImage(bitMatrix);
    }
}
