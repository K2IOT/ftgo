package net.ftgo.accounting.domain;

/** Raised when pending and successful refunds would exceed the captured amount. */
public class RefundExceedsCapturedAmountException extends IllegalStateException {

    public RefundExceedsCapturedAmountException(String message) {
        super(message);
    }
}
