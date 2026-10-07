package server;

import config.ConfigStore;
import print.PrinterResolver;

import java.io.IOException;
import java.io.InputStream;

/**
 * Titik masuk mode sidecar: {@code java -jar vandebooth.jar --server [--port N] [--exit-on-stdin-eof]}.
 * Token dibaca dari env VANDEBOOTH_TOKEN (bukan argumen, agar tidak terlihat di daftar proses).
 * Setelah siap, mencetak satu baris {@code VANDEBOOTH_LISTENING port=<n>} ke stdout.
 */
public final class SidecarMain {

    public static final String TOKEN_ENV = "VANDEBOOTH_TOKEN";
    public static final String READY_PREFIX = "VANDEBOOTH_LISTENING port=";

    private SidecarMain() {
    }

    public static void run(String[] args) throws IOException {
        int port = 0;
        boolean exitOnStdinEof = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--server" -> { }
                case "--port" -> port = Integer.parseInt(args[++i]);
                case "--exit-on-stdin-eof" -> exitOnStdinEof = true;
                default -> throw new IllegalArgumentException("Argumen tidak dikenal: " + args[i]);
            }
        }
        String token = System.getenv(TOKEN_ENV);
        if (token == null || token.length() < 16) {
            System.err.println("Env " + TOKEN_ENV + " wajib diisi (minimal 16 karakter).");
            System.exit(2);
        }

        System.setProperty("java.awt.headless", "true");
        SidecarApp app = SidecarApp.start(ConfigStore.load(), PrinterResolver.system(), token, port);
        Runtime.getRuntime().addShutdownHook(new Thread(app::close, "sidecar-shutdown"));

        System.out.println(READY_PREFIX + app.port());
        System.out.flush();

        if (exitOnStdinEof) {
            // Proses induk (Electron) menutup stdin saat keluar atau crash -> sidecar ikut mati
            Thread watcher = new Thread(() -> {
                try (InputStream in = System.in) {
                    while (in.read() != -1) {
                        // abaikan input
                    }
                } catch (IOException ignored) {
                    // stdin tertutup
                }
                System.exit(0);
            }, "stdin-watcher");
            watcher.setDaemon(true);
            watcher.start();
        }
    }
}
