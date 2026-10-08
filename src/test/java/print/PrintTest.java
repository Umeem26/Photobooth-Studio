package print;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import config.AppConfig;
import config.ConfigStore;
import exception.BoothException;
import server.FakePrinters;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.Printable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import javax.print.attribute.standard.Copies;

class PrintTest {

    @TempDir
    Path tmp;

    private static BufferedImage strip(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, w, h);
        g.dispose();
        return img;
    }

    @Test
    void composerKeepsRatioWithThreeMillimetreMargins() {
        BufferedImage page = PrintPageComposer.compose(strip(780, 2300), false);
        assertEquals(1200, page.getWidth());
        assertEquals(1800, page.getHeight());
        assertEquals(35, PrintPageComposer.MARGIN);
        Rectangle r = PrintPageComposer.fit(780, 2300, new Rectangle(35, 35, 1130, 1730));
        assertEquals(780.0 / 2300, (double) r.width / r.height, 0.01);
        assertEquals(Color.WHITE.getRGB(), page.getRGB(10, 900), "margin putih");
        assertEquals(Color.RED.getRGB(), page.getRGB(600, 900), "strip di tengah");
    }

    @Test
    void twoUpOnlyForStripsAndLandscapeLayoutsPrintSingle() {
        BufferedImage page = PrintPageComposer.compose(strip(780, 2300), true);
        assertEquals(Color.RED.getRGB(), page.getRGB(300, 900));
        assertEquals(Color.RED.getRGB(), page.getRGB(900, 900));
        assertEquals(Color.WHITE.getRGB(), page.getRGB(600, 900), "celah potong di tengah");

        // Layout 6x4 (lanskap) dan 4x6 (grid-6) selalu tunggal, two-up diabaikan
        BufferedImage land = PrintPageComposer.compose(strip(1800, 1200), true);
        assertEquals(1800, land.getWidth());
        assertEquals(1200, land.getHeight());
        assertEquals(Color.RED.getRGB(), land.getRGB(900, 300));
        assertEquals(Color.RED.getRGB(), land.getRGB(900, 600), "tidak ada celah potong: satu gambar utuh");
        BufferedImage portrait = PrintPageComposer.compose(strip(1200, 1800), true);
        assertEquals(1200, portrait.getWidth());
        assertEquals(Color.RED.getRGB(), portrait.getRGB(600, 900), "grid-6 tunggal, tanpa celah di tengah");
        assertTrue(PrintPageComposer.isTwoUpStrip(strip(600, 1800)));
        assertFalse(PrintPageComposer.isTwoUpStrip(strip(1200, 1800)));
        assertFalse(PrintPageComposer.isTwoUpStrip(strip(1800, 1200)));
    }

    @Test
    void printsToFakePrintServiceWithCopiesAndPrintable() throws Exception {
        FakePrinters printers = new FakePrinters("Booth Printer");
        PrintExportStrategy s = new PrintExportStrategy(printers, "", 2, false, null, "job1");
        assertTrue(s.export(strip(780, 2300), null), s.lastError());
        assertEquals(1, printers.printed.size());
        FakePrinters.Printed job = printers.printed.get(0);
        assertEquals(2, ((Copies) job.attributes().get(Copies.class)).getValue());

        Printable printable = (Printable) job.doc().getPrintData();
        PageFormat pf = new PageFormat();
        Paper paper = new Paper();
        paper.setSize(288, 432);
        paper.setImageableArea(9, 9, 270, 414);
        pf.setPaper(paper);
        BufferedImage canvas = new BufferedImage(288, 432, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = canvas.createGraphics();
        assertEquals(Printable.PAGE_EXISTS, printable.print(g, pf, 0));
        assertEquals(Printable.NO_SUCH_PAGE, printable.print(g, pf, 1));
        g.dispose();
        assertEquals(Color.RED.getRGB(), canvas.getRGB(144, 216));
    }

    @Test
    void missingPrinterFailsWithReason() {
        PrintExportStrategy none = new PrintExportStrategy(new FakePrinters(), "", 1, false, null, "j");
        assertFalse(none.export(strip(10, 30), null));
        assertTrue(none.lastError().contains("printer"));
        PrintExportStrategy named = new PrintExportStrategy(new FakePrinters("A"), "B", 1, false, null, "j");
        assertFalse(named.export(strip(10, 30), null));
    }

    @Test
    void fileModeWritesOnePngPerCopy() throws Exception {
        Path queue = tmp.resolve("print-queue");
        PrintExportStrategy s = new PrintExportStrategy(new FakePrinters(), "", 2, true, queue, "sesi");
        assertTrue(s.export(strip(780, 2300), null));
        try (Stream<Path> files = Files.list(queue)) {
            assertEquals(2, files.count());
        }
    }

    private PrintManager manager(FakePrinters printers, String mode) {
        Properties p = new Properties();
        p.setProperty(AppConfig.OUTPUT_DIR_KEY, tmp.toString());
        p.setProperty(AppConfig.PRINT_MODE_KEY, mode);
        return new PrintManager(ConfigStore.of(p, null), printers, Executors.newSingleThreadExecutor());
    }

    private static PrintManager.JobStatus await(PrintManager m, String key) throws InterruptedException {
        for (int i = 0; i < 200; i++) {
            PrintManager.JobStatus s = m.status(key);
            if (s.state() == PrintManager.State.DONE || s.state() == PrintManager.State.FAILED) return s;
            Thread.sleep(25);
        }
        fail("print tidak selesai");
        return null;
    }

    @Test
    void managerQueuesAsyncAndEnforcesMaxCopies() throws Exception {
        Path stripFile = tmp.resolve("strip.png");
        ImageIO.write(strip(780, 2300), "png", stripFile.toFile());
        FakePrinters printers = new FakePrinters("P1");
        PrintManager m = manager(printers, "system");

        assertEquals(PrintManager.State.IDLE, m.status("s1").state());
        m.submit("s1", stripFile, 1);
        assertEquals(PrintManager.State.DONE, await(m, "s1").state());
        m.submit("s1", stripFile, 1);
        assertEquals(PrintManager.State.DONE, await(m, "s1").state());
        BoothException limit = assertThrows(BoothException.class, () -> m.submit("s1", stripFile, 1));
        assertEquals(409, limit.kind().status());
        assertEquals(2, m.status("s1").copiesUsed());
        assertEquals(2, printers.printed.size());
        assertEquals("ready", m.printers().get(0).status());
        assertTrue(m.printers().get(0).isDefault());
        m.close();
    }

    @Test
    void failedPrintIsReportedAndNotCounted() throws Exception {
        Path stripFile = tmp.resolve("strip.png");
        ImageIO.write(strip(780, 2300), "png", stripFile.toFile());
        FakePrinters printers = new FakePrinters("P1");
        printers.services.get(0).fail = true;
        PrintManager m = manager(printers, "system");
        m.submit("s1", stripFile, 1);
        PrintManager.JobStatus s = await(m, "s1");
        assertEquals(PrintManager.State.FAILED, s.state());
        assertTrue(s.error().contains("kertas habis"));
        assertEquals(0, s.copiesUsed());

        PrintManager none = manager(new FakePrinters(), "system");
        none.submit("s2", stripFile, 1);
        assertEquals(PrintManager.State.FAILED, await(none, "s2").state());
        m.close();
        none.close();
    }

    @Test
    void fileModeAndTestPageNeedNoPrinter() throws Exception {
        PrintManager m = manager(new FakePrinters(), "file");
        m.printTest(tmp.resolve("tmp"));
        assertEquals(PrintManager.State.DONE, await(m, PrintManager.TEST_KEY).state());
        try (Stream<Path> files = Files.list(tmp.resolve("print-queue"))) {
            assertEquals(1, files.count());
        }
        m.close();
    }
}
