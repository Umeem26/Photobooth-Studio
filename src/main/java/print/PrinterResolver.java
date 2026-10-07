package print;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;

/** Sumber daftar printer; tes memakai implementasi palsu tanpa printer fisik. */
public interface PrinterResolver {

    List<PrintService> services();

    Optional<PrintService> defaultService();

    /** Printer bernama, atau default sistem bila nama kosong. */
    default Optional<PrintService> find(String name) {
        if (name == null || name.isBlank()) return defaultService();
        return services().stream().filter(s -> s.getName().equals(name)).findFirst();
    }

    /** Printer sistem lewat javax.print. */
    static PrinterResolver system() {
        return new PrinterResolver() {
            @Override
            public List<PrintService> services() {
                return Arrays.asList(PrintServiceLookup.lookupPrintServices(null, null));
            }

            @Override
            public Optional<PrintService> defaultService() {
                return Optional.ofNullable(PrintServiceLookup.lookupDefaultPrintService());
            }
        };
    }
}
