package server;

import print.PrinterResolver;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.print.Doc;
import javax.print.DocFlavor;
import javax.print.DocPrintJob;
import javax.print.PrintException;
import javax.print.PrintService;
import javax.print.ServiceUIFactory;
import javax.print.attribute.Attribute;
import javax.print.attribute.AttributeSet;
import javax.print.attribute.HashPrintJobAttributeSet;
import javax.print.attribute.HashPrintServiceAttributeSet;
import javax.print.attribute.PrintJobAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.PrintServiceAttribute;
import javax.print.attribute.PrintServiceAttributeSet;
import javax.print.attribute.standard.PrinterIsAcceptingJobs;
import javax.print.event.PrintJobAttributeListener;
import javax.print.event.PrintJobListener;
import javax.print.event.PrintServiceAttributeListener;

/** Printer palsu untuk tes (tanpa printer fisik). Mencatat setiap job yang dicetak. */
public final class FakePrinters implements PrinterResolver {

    public record Printed(String printer, Doc doc, PrintRequestAttributeSet attributes) { }

    public final List<FakeService> services = new ArrayList<>();
    public final List<Printed> printed = new ArrayList<>();

    public FakePrinters(String... names) {
        for (String n : names) services.add(new FakeService(n));
    }

    @Override
    public List<PrintService> services() {
        return new ArrayList<>(services);
    }

    @Override
    public Optional<PrintService> defaultService() {
        return services.isEmpty() ? Optional.empty() : Optional.of(services.get(0));
    }

    public final class FakeService implements PrintService {
        private final String name;
        public boolean fail;

        FakeService(String name) {
            this.name = name;
        }

        @Override public String getName() { return name; }

        @Override
        public DocPrintJob createPrintJob() {
            PrintService self = this;
            return new DocPrintJob() {
                @Override public PrintService getPrintService() { return self; }
                @Override public PrintJobAttributeSet getAttributes() { return new HashPrintJobAttributeSet(); }
                @Override public void addPrintJobListener(PrintJobListener l) { }
                @Override public void removePrintJobListener(PrintJobListener l) { }
                @Override public void addPrintJobAttributeListener(PrintJobAttributeListener l, PrintJobAttributeSet a) { }
                @Override public void removePrintJobAttributeListener(PrintJobAttributeListener l) { }

                @Override
                public void print(Doc doc, PrintRequestAttributeSet attrs) throws PrintException {
                    if (fail) throw new PrintException("kertas habis");
                    synchronized (printed) {
                        printed.add(new Printed(name, doc, attrs));
                    }
                }
            };
        }

        @Override public void addPrintServiceAttributeListener(PrintServiceAttributeListener l) { }
        @Override public void removePrintServiceAttributeListener(PrintServiceAttributeListener l) { }
        @Override public PrintServiceAttributeSet getAttributes() { return new HashPrintServiceAttributeSet(); }

        @Override
        @SuppressWarnings("unchecked")
        public <T extends PrintServiceAttribute> T getAttribute(Class<T> category) {
            return category == PrinterIsAcceptingJobs.class ? (T) PrinterIsAcceptingJobs.ACCEPTING_JOBS : null;
        }

        @Override public DocFlavor[] getSupportedDocFlavors() { return new DocFlavor[] {DocFlavor.SERVICE_FORMATTED.PRINTABLE}; }
        @Override public boolean isDocFlavorSupported(DocFlavor f) { return DocFlavor.SERVICE_FORMATTED.PRINTABLE.equals(f); }
        @Override public Class<?>[] getSupportedAttributeCategories() { return new Class<?>[0]; }
        @Override public boolean isAttributeCategorySupported(Class<? extends Attribute> c) { return true; }
        @Override public Object getDefaultAttributeValue(Class<? extends Attribute> c) { return null; }
        @Override public Object getSupportedAttributeValues(Class<? extends Attribute> c, DocFlavor f, AttributeSet a) { return null; }
        @Override public boolean isAttributeValueSupported(Attribute v, DocFlavor f, AttributeSet a) { return true; }
        @Override public AttributeSet getUnsupportedAttributes(DocFlavor f, AttributeSet a) { return null; }
        @Override public ServiceUIFactory getServiceUIFactory() { return null; }
    }
}
