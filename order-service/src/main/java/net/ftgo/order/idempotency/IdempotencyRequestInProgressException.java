package net.ftgo.order.idempotency;

public class IdempotencyRequestInProgressException extends RuntimeException {

    public IdempotencyRequestInProgressException() {
        super("A request with this idempotency key is still processing");
    }
}
