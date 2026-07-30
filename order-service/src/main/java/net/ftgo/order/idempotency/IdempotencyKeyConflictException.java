package net.ftgo.order.idempotency;

public class IdempotencyKeyConflictException extends RuntimeException {

    public IdempotencyKeyConflictException() {
        super("Idempotency key was already used with a different request");
    }
}
