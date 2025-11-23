package export;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.SwingConstants;

// Google Drive Imports
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.Permission;

// QR Code Imports (ZXing)
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

public class DriveExportStrategy implements ExportStrategy {

    private static final String APPLICATION_NAME = "Photobooth App";
    private static final GsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String TOKENS_DIRECTORY_PATH = "tokens";
    private static final List<String> SCOPES = Collections.singletonList(DriveScopes.DRIVE_FILE);
    
    // Nama file credential yang Anda simpan di folder src
    private static final String CREDENTIALS_FILE_PATH = "/credentials.json"; 

    @Override
    public String getStrategyName() { return "Upload ke Google Drive (QR Code)"; }

    @Override
    public boolean export(BufferedImage image) {
        System.out.println("LOG: Memulai proses upload ke Drive...");
        
        // 1. Simpan gambar sementara ke disk agar bisa diupload
        File tempFile = new File("temp_upload.png");
        try {
            ImageIO.write(image, "PNG", tempFile);
        } catch (IOException e) {
            System.err.println("Gagal menyimpan file sementara: " + e.getMessage());
            return false;
        }

        try {
            // 2. Login & Setup Drive Service
            Drive service = getDriveService();

            // 3. Siapkan Metadata File
            com.google.api.services.drive.model.File fileMetadata = new com.google.api.services.drive.model.File();
            fileMetadata.setName("Foto_Photobooth_" + System.currentTimeMillis() + ".png");

            // 4. Siapkan Konten File
            FileContent mediaContent = new FileContent("image/png", tempFile);

            // 5. Eksekusi Upload
            System.out.println("LOG: Mengupload file...");
            com.google.api.services.drive.model.File uploadedFile = service.files().create(fileMetadata, mediaContent)
                    .setFields("id, webViewLink") // Minta kembalian ID dan Link
                    .execute();
            
            System.out.println("File ID: " + uploadedFile.getId());

            // 6. Ubah Izin jadi PUBLIC (Agar bisa discan siapa saja)
            Permission userPermission = new Permission()
                    .setType("anyone")
                    .setRole("reader");
            service.permissions().create(uploadedFile.getId(), userPermission).execute();

            // 7. Ambil Link
            String shareLink = uploadedFile.getWebViewLink();
            System.out.println("Link Drive: " + shareLink);

            // 8. Generate QR Code
            BufferedImage qrImage = generateQRCode(shareLink);

            // 9. Tampilkan QR Code
            showQRCodeDialog(qrImage);

            // Bersihkan file sementara
            tempFile.delete();
            return true;

        } catch (Exception e) {
            // Tampilkan error di GUI agar terlihat
            JOptionPane.showMessageDialog(null, "Gagal Upload: " + e.getMessage(), "Error Drive", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
            return false;
        }
    }

    // --- Helper untuk Login Google ---
    private Drive getDriveService() throws Exception {
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        
        // Cari file credentials.json di folder src
        InputStream in = DriveExportStrategy.class.getResourceAsStream(CREDENTIALS_FILE_PATH);
        if (in == null) {
            throw new IOException("File 'credentials.json' tidak ditemukan di folder src!");
        }
        
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        // Alur Login OAuth2
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build();
        
        // Buka browser lokal untuk login
        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
        Credential credential = new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");

        return new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    // --- Helper untuk Bikin QR Code ---
    private BufferedImage generateQRCode(String text) throws Exception {
        QRCodeWriter barcodeWriter = new QRCodeWriter();
        BitMatrix bitMatrix = barcodeWriter.encode(text, BarcodeFormat.QR_CODE, 300, 300);
        return MatrixToImageWriter.toBufferedImage(bitMatrix);
    }

    // --- Helper Tampilan ---
    private void showQRCodeDialog(BufferedImage qrImage) {
        JLabel qrLabel = new JLabel(new ImageIcon(qrImage));
        JLabel textLabel = new JLabel("Scan QR Code ini untuk download foto!", SwingConstants.CENTER);
        textLabel.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 16));
        
        JOptionPane.showMessageDialog(null, 
            new Object[]{textLabel, qrLabel}, 
            "Upload Berhasil!", 
            JOptionPane.PLAIN_MESSAGE);
    }
}