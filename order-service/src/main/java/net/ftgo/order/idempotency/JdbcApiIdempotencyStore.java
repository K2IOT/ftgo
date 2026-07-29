package net.ftgo.order.idempotency;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

@Repository
public class JdbcApiIdempotencyStore implements ApiIdempotencyStore {

    private static final String INSERT_PROCESSING = """
        INSERT IGNORE INTO api_idempotency_records (
          consumer_id,
          operation,
          idempotency_key,
          request_hash,
          state,
          expires_at
        ) VALUES (?, ?, ?, ?, 'PROCESSING', ?)
        """;

    private static final String LOCK_RECORD = """
        SELECT consumer_id,
               operation,
               idempotency_key,
               request_hash,
               state,
               http_status,
               response_json,
               resource_id,
               created_at,
               updated_at,
               expires_at
          FROM api_idempotency_records
         WHERE consumer_id = ?
           AND operation = ?
           AND idempotency_key = ?
         FOR UPDATE
        """;

    private static final String COMPLETE = """
        UPDATE api_idempotency_records
           SET state = 'COMPLETED',
               http_status = ?,
               response_json = ?,
               resource_id = ?,
               updated_at = CURRENT_TIMESTAMP(6)
         WHERE consumer_id = ?
           AND operation = ?
           AND idempotency_key = ?
           AND state = 'PROCESSING'
        """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcApiIdempotencyStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean insertProcessing(
        Long consumerId,
        String operation,
        String key,
        byte[] requestHash,
        Instant expiresAt
    ) {
        return jdbcTemplate.update(
            INSERT_PROCESSING,
            consumerId,
            operation,
            key,
            requestHash,
            Timestamp.from(expiresAt)
        ) == 1;
    }

    @Override
    public Optional<ApiIdempotencyRecord> lock(
        Long consumerId,
        String operation,
        String key
    ) {
        return jdbcTemplate.query(
            LOCK_RECORD,
            this::mapRecord,
            consumerId,
            operation,
            key
        ).stream().findFirst();
    }

    @Override
    public void complete(
        Long consumerId,
        String operation,
        String key,
        int httpStatus,
        String responseJson,
        Long resourceId
    ) {
        int updated = jdbcTemplate.update(
            COMPLETE,
            httpStatus,
            responseJson,
            resourceId,
            consumerId,
            operation,
            key
        );
        if (updated != 1) {
            throw new IllegalStateException(
                "Idempotency record is not in PROCESSING state for operation " + operation
            );
        }
    }

    private ApiIdempotencyRecord mapRecord(ResultSet rs, int rowNum) throws SQLException {
        Timestamp createdAt = rs.getTimestamp("created_at");
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        Timestamp expiresAt = rs.getTimestamp("expires_at");
        Number httpStatus = (Number) rs.getObject("http_status");
        Number resourceId = (Number) rs.getObject("resource_id");
        return new ApiIdempotencyRecord(
            rs.getLong("consumer_id"),
            rs.getString("operation"),
            rs.getString("idempotency_key"),
            rs.getBytes("request_hash"),
            ApiIdempotencyRecord.State.valueOf(rs.getString("state")),
            httpStatus == null ? null : httpStatus.intValue(),
            rs.getString("response_json"),
            resourceId == null ? null : resourceId.longValue(),
            createdAt.toInstant(),
            updatedAt.toInstant(),
            expiresAt.toInstant()
        );
    }
}
