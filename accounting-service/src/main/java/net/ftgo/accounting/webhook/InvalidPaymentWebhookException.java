package net.ftgo.accounting.webhook;

/** Raised when a payment webhook cannot be authenticated or safely parsed. */
public class InvalidPaymentWebhookException extends RuntimeException {

    public InvalidPaymentWebhookException(String message) {
        super(message);
    }

    public InvalidPaymentWebhookException(String message, Throwable cause) {
        super(message, cause);
    }
}
