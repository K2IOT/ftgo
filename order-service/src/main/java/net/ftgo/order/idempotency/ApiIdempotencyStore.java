package net.ftgo.order.idempotency;

import java.time.Instant;
import java.util.Optional;

public interface ApiIdempotencyStore {

    boolean insertProcessing(
        Long consumerId,
        String operation,
        String key,
        byte[] requestHash,
        Instant expiresAt
    );

    Optional<ApiIdempotencyRecord> lock(Long consumerId, String operation, String key);

    void complete(
        Long consumerId,
        String operation,
        String key,
        int httpStatus,
        String responseJson,
        Long resourceId
    );
}
