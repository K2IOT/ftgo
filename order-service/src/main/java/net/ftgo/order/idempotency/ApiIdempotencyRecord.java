package net.ftgo.order.idempotency;

import java.time.Instant;
import java.util.Arrays;

public record ApiIdempotencyRecord(
    Long consumerId,
    String operation,
    String idempotencyKey,
    byte[] requestHash,
    State state,
    Integer httpStatus,
    String responseJson,
    Long resourceId,
    Instant createdAt,
    Instant updatedAt,
    Instant expiresAt
) {

    public enum State {
        PROCESSING,
        COMPLETED
    }

    public ApiIdempotencyRecord {
        requestHash = requestHash == null ? null : Arrays.copyOf(requestHash, requestHash.length);
    }

    @Override
    public byte[] requestHash() {
        return requestHash == null ? null : Arrays.copyOf(requestHash, requestHash.length);
    }

    public static ApiIdempotencyRecord processing(
        Long consumerId,
        String operation,
        String idempotencyKey,
        byte[] requestHash,
        Instant createdAt,
        Instant expiresAt
    ) {
        return new ApiIdempotencyRecord(
            consumerId,
            operation,
            idempotencyKey,
            requestHash,
            State.PROCESSING,
            null,
            null,
            null,
            createdAt,
            createdAt,
            expiresAt
        );
    }

    public ApiIdempotencyRecord completed(
        int status,
        String response,
        Long completedResourceId,
        Instant completionTime
    ) {
        return new ApiIdempotencyRecord(
            consumerId,
            operation,
            idempotencyKey,
            requestHash,
            State.COMPLETED,
            status,
            response,
            completedResourceId,
            createdAt,
            completionTime,
            expiresAt
        );
    }
}
