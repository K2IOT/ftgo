package net.ftgo.common.messaging;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Deletes expired messaging rows in small, repeatable batches.
 *
 * <p>The worker intentionally preserves {@code PROCESSING} command claims because they
 * participate in command idempotency. Cleanup is disabled by default and must be
 * explicitly enabled by operations.</p>
 */
public class JdbcMessageRetentionWorker {

    private static final Logger LOGGER = LoggerFactory.getLogger(JdbcMessageRetentionWorker.class);
    private static final String OUTBOX = "outbox";
    private static final String PROCESSED_COMMANDS = "processed_commands";
    private static final String PROCESSING = "PROCESSING";

    private final String serviceName;
    private final JdbcTemplate jdbcTemplate;
    private final MessageRetentionProperties properties;
    private final Clock clock;
    private final TableMetrics outboxMetrics;
    private final TableMetrics processedCommandMetrics;

    public JdbcMessageRetentionWorker(
        String serviceName,
        JdbcTemplate jdbcTemplate,
        MeterRegistry meterRegistry,
        MessageRetentionProperties properties
    ) {
        this(serviceName, jdbcTemplate, meterRegistry, properties, Clock.systemUTC());
    }

    JdbcMessageRetentionWorker(
        String serviceName,
        JdbcTemplate jdbcTemplate,
        MeterRegistry meterRegistry,
        MessageRetentionProperties properties,
        Clock clock
    ) {
        this.serviceName = serviceName;
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        this.clock = clock;
        this.outboxMetrics = TableMetrics.register(meterRegistry, serviceName, OUTBOX);
        this.processedCommandMetrics = TableMetrics.register(
            meterRegistry,
            serviceName,
            PROCESSED_COMMANDS
        );
    }

    @Scheduled(fixedDelayString = "${ftgo.messaging.retention.interval:PT10M}")
    public void scheduledCleanup() {
        runOnce();
    }

    public void runOnce() {
        if (!properties.isEnabled()) {
            return;
        }

        Instant now = clock.instant();
        cleanupTable(
            OUTBOX,
            now.minus(properties.getOutboxRetention()),
            outboxMetrics,
            this::deleteOutboxBatch
        );
        cleanupTable(
            PROCESSED_COMMANDS,
            now.minus(properties.getCompletedCommandRetention()),
            processedCommandMetrics,
            this::deleteProcessedCommandBatch
        );
    }

    private void cleanupTable(
        String table,
        Instant cutoff,
        TableMetrics metrics,
        BatchDelete batchDelete
    ) {
        long startedAt = System.nanoTime();
        try {
            if (!tableExists(table)) {
                return;
            }

            int deleted = batchDelete.delete(cutoff, properties.getBatchSize());
            if (deleted > 0) {
                metrics.deletedRows().increment(deleted);
            }
            metrics.lastSuccessEpochSeconds().set(clock.instant().getEpochSecond());
        } catch (RuntimeException failure) {
            metrics.failures().increment();
            LOGGER.warn(
                "Messaging retention cleanup failed for service={} table={}",
                serviceName,
                table,
                failure
            );
        } finally {
            metrics.cleanupDuration().record(
                System.nanoTime() - startedAt,
                TimeUnit.NANOSECONDS
            );
        }
    }

    private boolean tableExists(String table) {
        Integer count = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
              FROM information_schema.tables
             WHERE table_schema = DATABASE()
               AND table_name = ?
            """,
            Integer.class,
            table
        );
        return count != null && count > 0;
    }

    private int deleteOutboxBatch(Instant cutoff, int batchSize) {
        return jdbcTemplate.update(
            """
            DELETE FROM outbox
             WHERE created_at < ?
             ORDER BY created_at ASC
             LIMIT ?
            """,
            Timestamp.from(cutoff),
            batchSize
        );
    }

    private int deleteProcessedCommandBatch(Instant cutoff, int batchSize) {
        return jdbcTemplate.update(
            """
            DELETE FROM processed_commands
             WHERE processed_at < ?
               AND outcome <> ?
             ORDER BY processed_at ASC
             LIMIT ?
            """,
            Timestamp.from(cutoff),
            PROCESSING,
            batchSize
        );
    }

    @FunctionalInterface
    private interface BatchDelete {
        int delete(Instant cutoff, int batchSize);
    }

    private record TableMetrics(
        Counter deletedRows,
        Counter failures,
        AtomicLong lastSuccessEpochSeconds,
        Timer cleanupDuration
    ) {
        private static TableMetrics register(
            MeterRegistry meterRegistry,
            String serviceName,
            String table
        ) {
            Counter deletedRows = Counter
                .builder("ftgo_message_retention_deleted_rows_total")
                .description("Rows deleted by messaging retention cleanup")
                .tag("service", serviceName)
                .tag("table", table)
                .register(meterRegistry);
            Counter failures = Counter
                .builder("ftgo_message_retention_failures_total")
                .description("Messaging retention cleanup failures")
                .tag("service", serviceName)
                .tag("table", table)
                .register(meterRegistry);
            AtomicLong lastSuccess = new AtomicLong(0L);
            Gauge
                .builder(
                    "ftgo_message_retention_last_success_epoch_seconds",
                    lastSuccess,
                    AtomicLong::doubleValue
                )
                .description("Epoch second of the last successful messaging retention cleanup")
                .tag("service", serviceName)
                .tag("table", table)
                .register(meterRegistry);
            Timer cleanupDuration = Timer
                .builder("ftgo_message_retention_cleanup_duration")
                .description("Messaging retention cleanup duration")
                .tag("service", serviceName)
                .tag("table", table)
                .register(meterRegistry);
            return new TableMetrics(deletedRows, failures, lastSuccess, cleanupDuration);
        }
    }
}
