package net.ftgo.order.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class ApiIdempotencyMySqlIntegrationTest {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
        .withDatabaseName("ftgo_order")
        .withUsername("ftgo_user")
        .withPassword("ftgo_password");

    private static JdbcTemplate jdbcTemplate;
    private static TransactionTemplate transactionTemplate;
    private static OrderMutationIdempotencyService service;

    @BeforeAll
    static void setUpDatabase() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
            MYSQL.getJdbcUrl(),
            MYSQL.getUsername(),
            MYSQL.getPassword()
        );
        jdbcTemplate = new JdbcTemplate(dataSource);
        transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        service = new OrderMutationIdempotencyService(
            new JdbcApiIdempotencyStore(jdbcTemplate),
            new ObjectMapper().findAndRegisterModules()
        );

        jdbcTemplate.execute("""
            CREATE TABLE api_idempotency_records (
              consumer_id BIGINT NOT NULL,
              operation VARCHAR(100) NOT NULL,
              idempotency_key VARCHAR(255) NOT NULL,
              request_hash BINARY(32) NOT NULL,
              state VARCHAR(20) NOT NULL,
              http_status INT NULL,
              response_json MEDIUMTEXT NULL,
              resource_id BIGINT NULL,
              created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
              updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                ON UPDATE CURRENT_TIMESTAMP(6),
              expires_at TIMESTAMP(6) NOT NULL,
              PRIMARY KEY (consumer_id, operation, idempotency_key)
            ) ENGINE=InnoDB
            """);
        jdbcTemplate.execute("""
            CREATE TABLE mutation_markers (
              marker_key VARCHAR(255) NOT NULL PRIMARY KEY,
              marker_value VARCHAR(255) NOT NULL
            ) ENGINE=InnoDB
            """);
    }

    @AfterAll
    static void stopDatabase() {
        MYSQL.stop();
    }

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM mutation_markers");
        jdbcTemplate.update("DELETE FROM api_idempotency_records");
    }

    @Test
    void twentyConcurrentRequestsCreateExactlyOneMutationAndReplayExactResponse()
        throws Exception {
        int requestCount = 20;
        AtomicInteger mutationCalls = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        byte[] hash = sha256Sized("concurrent-create-request");
        String responseJson = "{\"orderId\":9001}";

        try {
            List<Future<IdempotentResult<String>>> futures = new ArrayList<>();
            for (int index = 0; index < requestCount; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(30, TimeUnit.SECONDS));
                    return transactionTemplate.execute(status -> service.execute(
                        101L,
                        OrderMutationIdempotencyService.CREATE_ORDER,
                        "concurrent-create-key",
                        hash,
                        () -> {
                            mutationCalls.incrementAndGet();
                            jdbcTemplate.update(
                                "INSERT INTO mutation_markers(marker_key, marker_value) VALUES (?, ?)",
                                "create-9001",
                                "created"
                            );
                            try {
                                Thread.sleep(250L);
                            } catch (InterruptedException error) {
                                Thread.currentThread().interrupt();
                                throw new IllegalStateException("Mutation was interrupted", error);
                            }
                            return responseJson;
                        }
                    ));
                }));
            }

            assertTrue(ready.await(30, TimeUnit.SECONDS));
            start.countDown();

            List<IdempotentResult<String>> results = new ArrayList<>();
            for (Future<IdempotentResult<String>> future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }

            assertEquals(1, mutationCalls.get());
            assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mutation_markers",
                Integer.class
            ));
            assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM api_idempotency_records",
                Integer.class
            ));
            assertEquals(requestCount, results.size());
            assertTrue(results.stream().allMatch(result -> result.httpStatus() == 201));
            assertTrue(results.stream().allMatch(result -> responseJson.equals(result.responseBody())));
            assertTrue(results.stream().allMatch(result -> Long.valueOf(9001L).equals(result.resourceId())));
            assertEquals(1, results.stream().filter(result -> !result.replayed()).count());
            assertEquals(requestCount - 1L, results.stream().filter(IdempotentResult::replayed).count());
            Set<String> responseBodies = results.stream()
                .map(IdempotentResult::responseBody)
                .collect(Collectors.toSet());
            assertEquals(Set.of(responseJson), responseBodies);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void failedMutationRollsBackClaimAndBusinessWriteSoRetryCanSucceed() {
        byte[] hash = sha256Sized("rollback-create-request");

        assertThrows(IllegalStateException.class, () -> transactionTemplate.execute(status ->
            service.execute(
                101L,
                OrderMutationIdempotencyService.CREATE_ORDER,
                "rollback-create-key",
                hash,
                () -> {
                    jdbcTemplate.update(
                        "INSERT INTO mutation_markers(marker_key, marker_value) VALUES (?, ?)",
                        "rollback-marker",
                        "must-roll-back"
                    );
                    throw new IllegalStateException("simulated saga start failure");
                }
            )
        ));

        assertEquals(0, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM mutation_markers",
            Integer.class
        ));
        assertEquals(0, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM api_idempotency_records",
            Integer.class
        ));

        IdempotentResult<String> retry = transactionTemplate.execute(status -> service.execute(
            101L,
            OrderMutationIdempotencyService.CREATE_ORDER,
            "rollback-create-key",
            hash,
            () -> {
                jdbcTemplate.update(
                    "INSERT INTO mutation_markers(marker_key, marker_value) VALUES (?, ?)",
                    "rollback-marker",
                    "retry-succeeded"
                );
                return "{\"orderId\":9002}";
            }
        ));

        assertEquals(201, retry.httpStatus());
        assertEquals("{\"orderId\":9002}", retry.responseBody());
        assertEquals(1, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM mutation_markers",
            Integer.class
        ));
        assertEquals("COMPLETED", jdbcTemplate.queryForObject(
            "SELECT state FROM api_idempotency_records",
            String.class
        ));
    }

    private static byte[] sha256Sized(String value) {
        byte[] source = value.getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[32];
        System.arraycopy(source, 0, result, 0, Math.min(source.length, result.length));
        return result;
    }
}
