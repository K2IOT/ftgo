package net.ftgo.accounting.settlement;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class SettlementReconciliationWorkRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-08-10T03:00:00Z");
    private static final Duration LEASE = Duration.ofMinutes(2);

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
        .withDatabaseName("accounting")
        .withUsername("ftgo")
        .withPassword("ftgo");

    private Connection connection;
    private SettlementReconciliationWorkRepository repository;

    @BeforeAll
    static void createSchema() throws Exception {
        try (Connection connection = newConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE authorizations (
                    id BIGINT NOT NULL PRIMARY KEY,
                    status VARCHAR(50) NOT NULL
                )
                """);
            statement.execute("""
                CREATE TABLE settlement_reconciliation_work (
                    authorization_id BIGINT NOT NULL,
                    next_attempt_at DATETIME(6) NOT NULL,
                    locked_until DATETIME(6) NULL,
                    attempt_count INT NOT NULL DEFAULT 0,
                    last_error VARCHAR(1000) NULL,
                    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                        ON UPDATE CURRENT_TIMESTAMP(6),
                    PRIMARY KEY (authorization_id),
                    CONSTRAINT fk_test_reconciliation_authorization
                        FOREIGN KEY (authorization_id) REFERENCES authorizations(id)
                        ON DELETE CASCADE,
                    INDEX idx_test_reconciliation_due
                        (next_attempt_at, authorization_id, locked_until)
                )
                """);
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        try (Connection reset = newConnection(); Statement statement = reset.createStatement()) {
            statement.executeUpdate("DELETE FROM settlement_reconciliation_work");
            statement.executeUpdate("DELETE FROM authorizations");
        }
        connection = claimConnection();
        repository = repository(connection);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (connection != null && !connection.isClosed()) {
            connection.rollback();
            connection.close();
        }
    }

    @Test
    void claimsTwoHundredFiftyRowsAsHundredHundredFifty() throws Exception {
        insertAuthorizations(250);
        for (long id = 1; id <= 250; id++) {
            repository.enqueue(id, NOW.minusSeconds(1));
        }
        connection.commit();

        List<SettlementReconciliationWork> first = repository.claimDue(100, NOW, LEASE);
        connection.commit();
        List<SettlementReconciliationWork> second = repository.claimDue(100, NOW, LEASE);
        connection.commit();
        List<SettlementReconciliationWork> third = repository.claimDue(100, NOW, LEASE);
        connection.commit();

        assertThat(first).hasSize(100);
        assertThat(second).hasSize(100);
        assertThat(third).hasSize(50);
        assertThat(first).extracting(SettlementReconciliationWork::authorizationId)
            .containsExactlyElementsOf(longRange(1, 100));
        assertThat(second).extracting(SettlementReconciliationWork::authorizationId)
            .containsExactlyElementsOf(longRange(101, 200));
        assertThat(third).extracting(SettlementReconciliationWork::authorizationId)
            .containsExactlyElementsOf(longRange(201, 250));
    }

    @Test
    void twoReplicasSkipEachOthersLockedRows() throws Exception {
        insertAuthorizations(2);
        repository.enqueue(1L, NOW.minusSeconds(1));
        repository.enqueue(2L, NOW.minusSeconds(1));
        connection.commit();

        List<SettlementReconciliationWork> firstReplica = repository.claimDue(1, NOW, LEASE);

        try (Connection secondConnection = claimConnection()) {
            SettlementReconciliationWorkRepository secondRepository = repository(secondConnection);
            List<SettlementReconciliationWork> secondReplica = secondRepository.claimDue(
                2,
                NOW,
                LEASE
            );

            assertThat(firstReplica).extracting(SettlementReconciliationWork::authorizationId)
                .containsExactly(1L);
            assertThat(secondReplica).extracting(SettlementReconciliationWork::authorizationId)
                .containsExactly(2L);
            secondConnection.commit();
        }
        connection.commit();
    }

    @Test
    void failedClaimBecomesRetryableOnlyAfterLeaseExpiry() throws Exception {
        insertAuthorizations(1);
        repository.enqueue(1L, NOW.minusSeconds(1));
        connection.commit();

        SettlementReconciliationWork claim = repository.claimDue(1, NOW, LEASE).get(0);
        connection.commit();
        repository.recordFailure(claim, "provider unavailable", NOW.plusSeconds(30));
        connection.commit();

        assertThat(repository.claimDue(1, NOW.plusSeconds(119), LEASE)).isEmpty();
        connection.commit();

        List<SettlementReconciliationWork> retried = repository.claimDue(
            1,
            NOW.plusSeconds(121),
            LEASE
        );

        assertThat(retried).hasSize(1);
        assertThat(retried.get(0).authorizationId()).isEqualTo(1L);
        assertThat(retried.get(0).attemptCount()).isEqualTo(2);
        assertThat(retried.get(0).lastError()).isEqualTo("provider unavailable");
        connection.commit();
    }

    private void insertAuthorizations(int count) throws Exception {
        try (Connection insertConnection = newConnection(); PreparedStatement statement = insertConnection
            .prepareStatement("INSERT INTO authorizations (id, status) VALUES (?, 'AUTHORIZED')")) {
            for (long id = 1; id <= count; id++) {
                statement.setLong(1, id);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private SettlementReconciliationWorkRepository repository(Connection connection) {
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource(connection, true);
        return new SettlementReconciliationWorkRepository(new JdbcTemplate(dataSource));
    }

    private static Connection claimConnection() throws Exception {
        Connection connection = newConnection();
        connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        connection.setAutoCommit(false);
        return connection;
    }

    private static Connection newConnection() throws Exception {
        return DriverManager.getConnection(
            MYSQL.getJdbcUrl(),
            MYSQL.getUsername(),
            MYSQL.getPassword()
        );
    }

    private List<Long> longRange(long first, long last) {
        return java.util.stream.LongStream.rangeClosed(first, last)
            .boxed()
            .toList();
    }
}
