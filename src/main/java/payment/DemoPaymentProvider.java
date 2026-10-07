package payment;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pembayaran demo: tanpa jaringan, tanpa gateway. Status menjadi PAID hanya saat
 * {@link #simulatePaid(String)} dipanggil (tombol "Simulate payment").
 */
public class DemoPaymentProvider implements PaymentProvider {

    /** Isi QR palsu; sengaja bukan format QRIS/e-wallet apa pun. */
    public static final String QR_PAYLOAD = "DEMO-PAYMENT-NOT-REAL";

    private final Map<String, PaymentStatus> payments = new ConcurrentHashMap<>();

    @Override
    public PaymentStatus start(String sessionId, int amount) {
        return payments.merge(sessionId, PaymentStatus.PENDING, (old, ignored) -> old);
    }

    @Override
    public PaymentStatus status(String sessionId) {
        return payments.getOrDefault(sessionId, PaymentStatus.NONE);
    }

    @Override
    public String qrPayload(String sessionId) {
        return QR_PAYLOAD;
    }

    @Override
    public boolean supportsSimulation() {
        return true;
    }

    @Override
    public PaymentStatus simulatePaid(String sessionId) {
        payments.put(sessionId, PaymentStatus.PAID);
        return PaymentStatus.PAID;
    }
}
