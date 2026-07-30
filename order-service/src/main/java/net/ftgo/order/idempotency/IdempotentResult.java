package net.ftgo.order.idempotency;

public record IdempotentResult<T>(
    int httpStatus,
    T responseBody,
    Long resourceId,
    boolean replayed
) {
}
