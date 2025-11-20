package exception;

// Menggantikan PaymentFailedException
public class ExportFailedException extends Exception {
    public ExportFailedException(String message) {
        super(message);
    }
}