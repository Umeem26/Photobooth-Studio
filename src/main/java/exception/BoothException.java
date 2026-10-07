package exception;

/**
 * Kegagalan operasi sesi booth yang punya padanan status HTTP di sidecar.
 */
public class BoothException extends Exception {

    public enum Kind {
        BAD_REQUEST(400, "bad_request"),
        PAYMENT_REQUIRED(402, "payment_required"),
        NOT_FOUND(404, "not_found"),
        CONFLICT(409, "conflict");

        private final int status;
        private final String code;

        Kind(int status, String code) {
            this.status = status;
            this.code = code;
        }

        public int status() { return status; }
        public String code() { return code; }
    }

    private final Kind kind;

    public BoothException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
