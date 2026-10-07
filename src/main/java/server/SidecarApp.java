package server;

import admin.AdminAuth;
import admin.AdminService;
import admin.PinStore;
import config.ConfigStore;
import print.PrinterResolver;
import service.PhotoboothService;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;

/**
 * Composition root sidecar: Facade (sesi, payment, share, print), Mode Operator, dan API HTTP.
 */
public final class SidecarApp implements AutoCloseable {

    private final PhotoboothService service;
    private final AdminService admin;
    private final SidecarServer api;

    private SidecarApp(PhotoboothService service, AdminService admin, SidecarServer api) {
        this.service = service;
        this.admin = admin;
        this.api = api;
    }

    public static SidecarApp start(ConfigStore store, PrinterResolver printers, String token, int port)
            throws IOException {
        Clock clock = Clock.systemDefaultZone();
        PhotoboothService service = PhotoboothService.forSidecar(store, printers);
        Path pinDir = store.configDir() != null ? store.configDir() : store.current().outputDir().resolve("config");
        AdminService admin = new AdminService(service, new AdminAuth(new PinStore(pinDir), clock), clock);
        SidecarServer api;
        try {
            api = new SidecarServer(service, admin, token, port);
        } catch (IOException | RuntimeException e) {
            service.shutdown();
            throw e;
        }
        api.start();
        return new SidecarApp(service, admin, api);
    }

    public int port() {
        return api.port();
    }

    public PhotoboothService service() {
        return service;
    }

    public AdminService admin() {
        return admin;
    }

    @Override
    public void close() {
        api.stop();
        service.shutdown();
    }
}
