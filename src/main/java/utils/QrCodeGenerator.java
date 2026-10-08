package utils;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.awt.image.BufferedImage;
import java.util.Map;

/**
 * Util pembuat gambar QR Code (ZXing). Tidak bergantung pada GUI.
 */
public final class QrCodeGenerator {

    private QrCodeGenerator() {
    }

    public static BufferedImage generate(String text, int size) throws WriterException {
        return generate(text, size, 4);
    }

    /**
     * @param margin quiet zone dalam modul (standar 4; 1 bila QR diletakkan di kartu putih
     *               yang sudah memberi ruang kosong, seperti di mockup)
     */
    public static BufferedImage generate(String text, int size, int margin) throws WriterException {
        if (text == null || text.isEmpty()) {
            throw new IllegalArgumentException("Teks QR tidak boleh kosong");
        }
        if (size <= 0) {
            throw new IllegalArgumentException("Ukuran QR harus positif");
        }
        if (margin < 0) {
            throw new IllegalArgumentException("Margin QR tidak boleh negatif");
        }
        BitMatrix bitMatrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size,
                Map.of(EncodeHintType.MARGIN, margin));
        return MatrixToImageWriter.toBufferedImage(bitMatrix);
    }
}
