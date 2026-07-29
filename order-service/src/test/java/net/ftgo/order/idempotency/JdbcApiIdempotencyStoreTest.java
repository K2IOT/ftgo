package net.ftgo.order.idempotency;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcApiIdempotencyStoreTest {

    private JdbcApiIdempotencyStore store;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
            "jdbc:h2:mem:idempotency;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
            "sa",
            ""
        );
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("DROP TABLE IF EXISTS api_idempotency_records");
        jdbcTemplate.execute("""
            CREATE TABLE api_idempotency_records (
              consumer_id BIGINT NOT NULL,
              operation VARCHAR(100) NOT NULL,
              idempotency_key VARCHAR(255) NOT NULL,
              request_hash VARBINARY(32) NOT NULL,
              state VARCHAR(20) NOT NULL,
              http_status INTEGER NULL,
              response_json CLOB NULL,
              resource_id BIGINT NULL,
              created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
              updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
              expires_at TIMESTAMP(6) NOT NULL,
              PRIMARY KEY (consumer_id, operation, idempotency_key)
            )
            """);
        store = new JdbcApiIdempotencyStore(jdbcTemplate);
    }

    @Test
    void insertProcessingClaimsOnlyOnce() {
        byte[] hash = "request-one".getBytes(StandardCharsets.UTF_8);

        assertTrue(store.insertProcessing(
            101L,
            "CREATE_ORDER",
            "create-101-1",
            hash,
            Instant.now().plusSeconds(3600)
        ));
        assertFalse(store.insertProcessing(
            101L,
            "CREATE_ORDER",
            "create-101-1",
            hash,
            Instant.now().plusSeconds(3600)
        ));

        ApiIdempotencyRecord record = store.lock(
            101L,
            "CREATE_ORDER",
            "create-101-1"
        ).orElseThrow();
        assertEquals(ApiIdempotencyRecord.State.PROCESSING, record.state());
        assertArrayEquals(hash, record.requestHash());
        assertNull(record.httpStatus());
        assertNull(record.responseJson());
    }

    @Test
    void completePersistsTheExactReplayResponse() {
        byte[] hash = "request-two".getBytes(StandardCharsets.UTF_8);
        store.insertProcessing(
            101L,
            "CREATE_ORDER",
            "create-101-2",
            hash,
            Instant.now().plusSeconds(3600)
        );

        String response = "{\"orderId\":9001}";
        store.complete(
            101L,
            "CREATE_ORDER",
            "create-101-2",
            201,
            response,
            9001L
        );

        ApiIdempotencyRecord record = store.lock(
            101L,
            "CREATE_ORDER",
            "create-101-2"
        ).orElseThrow();
        assertEquals(ApiIdempotencyRecord.State.COMPLETED, record.state());
        assertEquals(201, record.httpStatus());
        assertEquals(response, record.responseJson());
        assertEquals(9001L, record.resourceId());
    }
}
