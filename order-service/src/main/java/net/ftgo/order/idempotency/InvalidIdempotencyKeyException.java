package net.ftgo.order.idempotency;

public class InvalidIdempotencyKeyException extends RuntimeException {

    private final String errorCode;

    private InvalidIdempotencyKeyException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public static InvalidIdempotencyKeyException required() {
        return new InvalidIdempotencyKeyException(
            "Idempotency-Key header is required",
            "IDEMPOTENCY_KEY_REQUIRED"
        );
    }

    public static InvalidIdempotencyKeyException invalid() {
        return new InvalidIdempotencyKeyException(
            "Idempotency-Key header is invalid",
            "IDEMPOTENCY_KEY_INVALID"
        );
    }

    public String errorCode() {
        return errorCode;
    }
}
