package payment;

/** Status pembayaran satu sesi. */
public enum PaymentStatus {
    NONE, PENDING, PAID;

    public String id() {
        return name().toLowerCase();
    }
}
