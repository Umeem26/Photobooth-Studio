package payment;

/**
 * Penyedia pembayaran. Fase 3 hanya punya {@link DemoPaymentProvider}; gateway asli
 * bisa dipasang kelak dengan mengimplementasikan antarmuka ini.
 */
public interface PaymentProvider {

    /** Memulai (atau melanjutkan) pembayaran sesi. Idempoten. */
    PaymentStatus start(String sessionId, int amount);

    /** Status terkini; NONE bila belum dimulai. */
    PaymentStatus status(String sessionId);

    /** Isi QR yang ditampilkan ke tamu. */
    String qrPayload(String sessionId);

    /** true bila penyedia mendukung tombol "Simulate payment" (mode demo). */
    default boolean supportsSimulation() {
        return false;
    }

    /** Menandai sesi lunas tanpa transaksi nyata (hanya mode demo). */
    default PaymentStatus simulatePaid(String sessionId) {
        throw new UnsupportedOperationException("Simulasi tidak didukung oleh " + getClass().getSimpleName());
    }
}
