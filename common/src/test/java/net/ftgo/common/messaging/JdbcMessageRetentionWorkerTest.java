package net.ftgo.common.messaging;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcMessageRetentionWorkerTest {

    private static final Instant NOW = Instant.parse("2026-08-10T08:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Container
    private final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.36")
        .withDatabaseName("ftgo_retention")
        .withUsername("ftgo")
        .withPassword("ftgo");

    private JdbcTemplate jdbcTemplate;
    private SimpleMeterRegistry meterRegistry;
    private MessageRetentionProperties properties;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(mysql.getDriverClassName());
        dataSource.setUrl(mysql.getJdbcUrl());
        dataSource.setUsername(mysql.getUsername());
        dataSource.setPassword(mysql.getPassword());
        jdbcTemplate = new JdbcTemplate(dataSource);
        meterRegistry = new SimpleMeterRegistry();
        properties = new MessageRetentionProperties();
        properties.setEnabled(true);
        properties.setOutboxRetention(Duration.ofDays(30));
        properties.setCompletedCommandRetention(Duration.ofDays(30));
        properties.setBatchSize(500);
        properties.setInterval(Duration.ofMinutes(10));

        jdbcTemplate.execute("DROP TABLE IF EXISTS processed_commands");
        jdbcTemplate.execute("DROP TABLE IF EXISTS outbox");
        jdbcTemplate.execute("""
            CREATE TABLE outbox (
                id VARCHAR(36) NOT NULL PRIMARY KEY,
                created_at TIMESTAMP(6) NOT NULL
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE processed_commands (
                consumer_name VARCHAR(100) NOT NULL,
                command_id VARCHAR(255) NOT NULL,
                outcome VARCHAR(20) NOT NULL,
                processed_at TIMESTAMP(6) NOT NULL,
                PRIMARY KEY (consumer_name, command_id)
            )
            """);
    }

    @Test
    void disabledModeLeavesRowsUntouched() {
        properties.setEnabled(false);
        insertOutbox(NOW.minus(Duration.ofDays(40)));
        insertProcessed("SUCCESS", NOW.minus(Duration.ofDays(40)));

        worker(jdbcTemplate, meterRegistry, properties).runOnce();

        assertThat(count("outbox")).isEqualTo(1);
        assertThat(count("processed_commands")).isEqualTo(1);
        assertThat(counter("ftgo_message_retention_deleted_rows_total", "outbox")).isZero();
        assertThat(counter("ftgo_message_retention_deleted_rows_total", "processed_commands")).isZero();
    }

    @Test
    void deletesOnlyConfiguredBatchAndCanRepeatUntilBacklogIsDrained() {
        properties.setBatchSize(2);
        for (int i = 0; i < 5; i++) {
            insertOutbox(NOW.minus(Duration.ofDays(40)).plusSeconds(i));
            insertProcessed("SUCCESS", NOW.minus(Duration.ofDays(40)).plusSeconds(i));
        }

        JdbcMessageRetentionWorker worker = worker(jdbcTemplate, meterRegistry, properties);

        worker.runOnce();
        assertThat(count("outbox")).isEqualTo(3);
        assertThat(count("processed_commands")).isEqualTo(3);

        worker.runOnce();
        assertThat(count("outbox")).isEqualTo(1);
        assertThat(count("processed_commands")).isEqualTo(1);

        worker.runOnce();
        assertThat(count("outbox")).isZero();
        assertThat(count("processed_commands")).isZero();
        assertThat(counter("ftgo_message_retention_deleted_rows_total", "outbox")).isEqualTo(5.0);
        assertThat(counter("ftgo_message_retention_deleted_rows_total", "processed_commands")).isEqualTo(5.0);
    }

    @Test
    void cutoffIsExclusiveSoRowsExactlyAtRetentionBoundaryArePreserved() {
        Instant cutoff = NOW.minus(Duration.ofDays(30));
        insertOutbox(cutoff.minusNanos(1_000));
        insertOutbox(cutoff);
        insertOutbox(cutoff.plusNanos(1_000));
        insertProcessed("SUCCESS", cutoff.minusNanos(1_000));
        insertProcessed("SUCCESS", cutoff);
        insertProcessed("SUCCESS", cutoff.plusNanos(1_000));

        worker(jdbcTemplate, meterRegistry, properties).runOnce();

        assertThat(count("outbox")).isEqualTo(2);
        assertThat(count("processed_commands")).isEqualTo(2);
    }

    @Test
    void neverDeletesProcessingCommandClaims() {
        Instant old = NOW.minus(Duration.ofDays(90));
        insertProcessed("PROCESSING", old);
        insertProcessed("SUCCESS", old.plusSeconds(1));
        insertProcessed("FAILURE", old.plusSeconds(2));

        worker(jdbcTemplate, meterRegistry, properties).runOnce();

        assertThat(count("processed_commands")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT outcome FROM processed_commands",
            String.class
        )).isEqualTo("PROCESSING");
    }

    @Test
    void absentProcessedCommandTableIsSkippedWithoutFailure() {
        jdbcTemplate.execute("DROP TABLE processed_commands");
        insertOutbox(NOW.minus(Duration.ofDays(40)));

        worker(jdbcTemplate, meterRegistry, properties).runOnce();

        assertThat(count("outbox")).isZero();
        assertThat(counter("ftgo_message_retention_failures_total", "processed_commands")).isZero();
    }

    @Test
    void databaseFailureIsCountedPerTableAndDoesNotStopOtherCleanup() {
        JdbcTemplate failingJdbc = new JdbcTemplate() {
            @Override
            public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
                return requiredType.cast(Integer.valueOf(1));
            }

            @Override
            public int update(String sql, Object... args) {
                if (sql.contains("DELETE FROM outbox")) {
                    throw new DataAccessResourceFailureException("database unavailable");
                }
                return 0;
            }
        };
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        worker(failingJdbc, registry, properties).runOnce();

        assertThat(counter(registry, "ftgo_message_retention_failures_total", "outbox"))
            .isEqualTo(1.0);
        assertThat(counter(registry, "ftgo_message_retention_failures_total", "processed_commands"))
            .isZero();
        assertThat(gauge(registry, "ftgo_message_retention_last_success_epoch_seconds", "outbox"))
            .isZero();
        assertThat(gauge(registry, "ftgo_message_retention_last_success_epoch_seconds", "processed_commands"))
            .isEqualTo((double) NOW.getEpochSecond());
    }

    @Test
    void startupRejectsRetentionShorterThanSevenDays() {
        new ApplicationContextRunner()
            .withUserConfiguration(OutboxMetricsConfiguration.class)
            .withPropertyValues("ftgo.messaging.retention.outbox-retention=P6D")
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(rootCause(context.getStartupFailure()).getMessage())
                    .contains("outboxRetention must be at least P7D");
            });
    }

    @Test
    void startupRejectsBatchSizeAboveHardLimit() {
        new ApplicationContextRunner()
            .withUserConfiguration(OutboxMetricsConfiguration.class)
            .withPropertyValues("ftgo.messaging.retention.batch-size=501")
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(rootCause(context.getStartupFailure()).getMessage())
                    .contains("batchSize", "less than or equal to 500");
            });
    }

    private JdbcMessageRetentionWorker worker(
        JdbcTemplate jdbc,
        SimpleMeterRegistry registry,
        MessageRetentionProperties retentionProperties
    ) {
        return new JdbcMessageRetentionWorker(
            "test-service",
            jdbc,
            registry,
            retentionProperties,
            CLOCK
        );
    }

    private void insertOutbox(Instant createdAt) {
        jdbcTemplate.update(
            "INSERT INTO outbox (id, created_at) VALUES (?, ?)",
            UUID.randomUUID().toString(),
            Timestamp.from(createdAt)
        );
    }

    private void insertProcessed(String outcome, Instant processedAt) {
        String commandId = UUID.randomUUID().toString();
        jdbcTemplate.update(
            """
            INSERT INTO processed_commands
              (consumer_name, command_id, outcome, processed_at)
            VALUES (?, ?, ?, ?)
            """,
            "test-consumer",
            commandId,
            outcome,
            Timestamp.from(processedAt)
        );
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private double counter(String name, String table) {
        return counter(meterRegistry, name, table);
    }

    private static double counter(SimpleMeterRegistry registry, String name, String table) {
        return registry.get(name)
            .tag("service", "test-service")
            .tag("table", table)
            .counter()
            .count();
    }

    private static double gauge(SimpleMeterRegistry registry, String name, String table) {
        return registry.get(name)
            .tag("service", "test-service")
            .tag("table", table)
            .gauge()
            .value();
    }

    private static Throwable rootCause(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
