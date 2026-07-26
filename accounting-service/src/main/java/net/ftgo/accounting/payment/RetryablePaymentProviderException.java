package net.ftgo.accounting.payment;

/** Transient provider failure that must not be cached as a completed command result. */
public class RetryablePaymentProviderException extends RuntimeException {

    public RetryablePaymentProviderException(String message) {
        super(message);
    }
}
