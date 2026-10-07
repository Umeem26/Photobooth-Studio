import server.SidecarMain;

/**
 * Titik masuk Van de Booth. GUI Swing lama sudah dihapus (lihat tag git legacy-swing-ui);
 * antarmuka sekarang berupa UI Electron/React yang memakai sidecar ini.
 */
public class MainApp {

    public static void main(String[] args) throws Exception {
        if (java.util.Arrays.asList(args).contains("--server")) {
            SidecarMain.run(args);
            return;
        }
        System.err.println("Pemakaian: VANDEBOOTH_TOKEN=<token> java -jar vandebooth.jar --server [--port N] [--exit-on-stdin-eof]");
        System.err.println("Untuk aplikasi lengkap jalankan: npm run dev");
        System.exit(2);
    }
}
