package service;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import config.AppConfig;
import config.ConfigStore;
import exception.BoothException;
import payment.DemoPaymentProvider;
import payment.PaymentStatus;
import repository.SessionRepository;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import javax.imageio.ImageIO;

class PaymentFlowTest {

    @TempDir
    Path tmp;

    private static byte[] jpeg() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(64, 48, BufferedImage.TYPE_INT_RGB), "jpg", out);
        return out.toByteArray();
    }

    private SessionManager manager(boolean paymentOn) {
        Properties p = new Properties();
        p.setProperty(AppConfig.OUTPUT_DIR_KEY, tmp.toString());
        p.setProperty(AppConfig.PAYMENT_ENABLED_KEY, String.valueOf(paymentOn));
        ConfigStore store = ConfigStore.of(p, null);
        return new SessionManager(new SessionRepository(store.current().sessionsDir()), store, new DemoPaymentProvider());
    }

    @Test
    void demoProviderOnlyPaysWhenSimulated() {
        DemoPaymentProvider demo = new DemoPaymentProvider();
        assertEquals(PaymentStatus.NONE, demo.status("s"));
        assertEquals(PaymentStatus.PENDING, demo.start("s", 25000));
        assertEquals(PaymentStatus.PENDING, demo.start("s", 25000));
        assertEquals("DEMO-PAYMENT-NOT-REAL", demo.qrPayload("s"));
        assertTrue(demo.supportsSimulation());
        assertEquals(PaymentStatus.PAID, demo.simulatePaid("s"));
        assertEquals(PaymentStatus.PAID, demo.start("s", 25000), "start tidak mengembalikan ke pending");
    }

    @Test
    void captureIsRejectedWithPaymentRequiredUntilPaid() throws Exception {
        SessionManager m = manager(true);
        String id = m.createSession("vertical-3");
        BoothException ex = assertThrows(BoothException.class, () -> m.putFrame(id, 1, jpeg()));
        assertEquals(402, ex.kind().status());

        SessionManager.PaymentStart start = m.startPayment(id);
        assertEquals(PaymentStatus.PENDING, start.status());
        assertEquals(25000, start.amount());
        assertThrows(BoothException.class, () -> m.putFrame(id, 1, jpeg()));

        assertEquals(PaymentStatus.PAID, m.simulatePayment(id));
        m.putFrame(id, 1, jpeg());
        assertEquals("paid", m.repository().readMeta(id).getProperty("payment"));
    }

    @Test
    void paymentOffAllowsCaptureAndRejectsPaymentCalls() throws Exception {
        SessionManager m = manager(false);
        String id = m.createSession("vertical-3");
        m.putFrame(id, 1, jpeg());
        assertEquals(PaymentStatus.PAID, m.paymentStatus(id));
        assertEquals(409, assertThrows(BoothException.class, () -> m.startPayment(id)).kind().status());
        assertEquals("off", m.repository().readMeta(id).getProperty("payment"));
    }

    @Test
    void startOverCarriesPaymentButNewSessionAfterResultDoesNot() throws Exception {
        SessionManager m = manager(true);
        String first = m.createSession("vertical-3");
        m.simulatePayment(first);

        String continued = m.createSession("vertical-3", first);
        assertEquals(PaymentStatus.PAID, m.paymentStatus(continued));
        m.putFrame(continued, 1, jpeg());

        String unpaidPrev = m.createSession("vertical-3");
        String notCarried = m.createSession("vertical-3", unpaidPrev);
        assertNotEquals(PaymentStatus.PAID, m.paymentStatus(notCarried));
    }

    @Test
    void togglingPaymentAffectsOnlyNewSessions() throws Exception {
        Properties p = new Properties();
        p.setProperty(AppConfig.OUTPUT_DIR_KEY, tmp.toString());
        ConfigStore store = ConfigStore.of(p, null);
        SessionManager m = new SessionManager(new SessionRepository(store.current().sessionsDir()), store);
        String before = m.createSession("vertical-3");
        store.update(Map.of(AppConfig.PAYMENT_ENABLED_KEY, "true"));
        String after = m.createSession("vertical-3");
        m.putFrame(before, 1, jpeg());
        assertEquals(402, assertThrows(BoothException.class, () -> m.putFrame(after, 1, jpeg())).kind().status());
    }
}
