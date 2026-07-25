package net.ftgo.common.messaging;

/** Signals a transient event-processing failure eligible for bounded retry. */
public class RetryableEventException extends RuntimeException {

    public RetryableEventException(String message) {
        super(message);
    }

    public RetryableEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
