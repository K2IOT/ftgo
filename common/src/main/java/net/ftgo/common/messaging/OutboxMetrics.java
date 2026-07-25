package net.ftgo.common.messaging;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;

/**
 * Outbox observability for Debezium-driven tables.
 *
 * <p>The legacy {@code published} column is intentionally ignored because the
 * Debezium Outbox Event Router never updates it. The count metric is an honest
 * row-backlog proxy and is paired with row age and cleanup eligibility.</p>
 */
public class OutboxMetrics {

    private final String serviceName;
    private final JdbcTemplate jdbcTemplate;
    private final Duration retention;
    private final Counter publishErrorCounter;

    public OutboxMetrics(
        String serviceName,
        JdbcTemplate jdbcTemplate,
        MeterRegistry meterRegistry,
        Duration retention
    ) {
        this.serviceName = serviceName;
        this.jdbcTemplate = jdbcTemplate;
        this.retention = retention;
        this.publishErrorCounter = Counter.builder("ftgo_outbox_publish_error_total")
            .description("Transactional outbox serialization or insert failures")
            .tag("service", serviceName)
            .register(meterRegistry);

        Gauge.builder("ftgo_outbox_unpublished_count", this, OutboxMetrics::rowBacklog)
            .description("Outbox row-backlog proxy; Debezium delivery is verified separately")
            .tag("service", serviceName)
            .tag("semantics", "row_backlog_proxy")
            .register(meterRegistry);
        Gauge.builder("ftgo_outbox_oldest_age_seconds", this, OutboxMetrics::oldestAgeSeconds)
            .description("Age in seconds of the oldest retained outbox row")
            .tag("service", serviceName)
            .register(meterRegistry);
        Gauge.builder("ftgo_outbox_cleanup_eligible_count", this, OutboxMetrics::cleanupEligibleCount)
            .description("Outbox rows older than the configured retention threshold")
            .tag("service", serviceName)
            .register(meterRegistry);
    }

    private OutboxMetrics() {
        this.serviceName = "noop";
        this.jdbcTemplate = null;
        this.retention = Duration.ofDays(7);
        this.publishErrorCounter = null;
    }

    public static OutboxMetrics noop() {
        return new OutboxMetrics();
    }

    public void recordPublishError() {
        if (publishErrorCounter != null) {
            publishErrorCounter.increment();
        }
    }

    public double rowBacklog() {
        if (jdbcTemplate == null) return 0.0;
        try {
            Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM outbox", Long.class);
            return count == null ? 0.0 : count.doubleValue();
        } catch (RuntimeException e) {
            return Double.NaN;
        }
    }

    public double oldestAgeSeconds() {
        if (jdbcTemplate == null) return 0.0;
        try {
            Timestamp oldest = jdbcTemplate.queryForObject(
                "SELECT MIN(created_at) FROM outbox",
                Timestamp.class
            );
            if (oldest == null) return 0.0;
            return Math.max(0L, Duration.between(oldest.toInstant(), Instant.now()).getSeconds());
        } catch (RuntimeException e) {
            return Double.NaN;
        }
    }

    public double cleanupEligibleCount() {
        if (jdbcTemplate == null) return 0.0;
        try {
            Timestamp cutoff = Timestamp.from(Instant.now().minus(retention));
            Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox WHERE created_at < ?",
                Long.class,
                cutoff
            );
            return count == null ? 0.0 : count.doubleValue();
        } catch (RuntimeException e) {
            return Double.NaN;
        }
    }

    public String getServiceName() {
        return serviceName;
    }
}
