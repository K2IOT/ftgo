package net.ftgo.common.messaging;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "ftgo.messaging.retention")
public class MessageRetentionProperties {

    private static final Duration MINIMUM_RETENTION = Duration.ofDays(7);

    private boolean enabled = false;

    @NotNull
    private Duration outboxRetention = Duration.ofDays(30);

    @NotNull
    private Duration completedCommandRetention = Duration.ofDays(30);

    @Min(1)
    @Max(500)
    private int batchSize = 500;

    @NotNull
    private Duration interval = Duration.ofMinutes(10);

    @AssertTrue(message = "outboxRetention must be at least P7D")
    public boolean isOutboxRetentionValid() {
        return outboxRetention == null || outboxRetention.compareTo(MINIMUM_RETENTION) >= 0;
    }

    @AssertTrue(message = "completedCommandRetention must be at least P7D")
    public boolean isCompletedCommandRetentionValid() {
        return completedCommandRetention == null
            || completedCommandRetention.compareTo(MINIMUM_RETENTION) >= 0;
    }

    @AssertTrue(message = "interval must be greater than zero")
    public boolean isIntervalValid() {
        return interval == null || !interval.isZero() && !interval.isNegative();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getOutboxRetention() {
        return outboxRetention;
    }

    public void setOutboxRetention(Duration outboxRetention) {
        this.outboxRetention = outboxRetention;
    }

    public Duration getCompletedCommandRetention() {
        return completedCommandRetention;
    }

    public void setCompletedCommandRetention(Duration completedCommandRetention) {
        this.completedCommandRetention = completedCommandRetention;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public Duration getInterval() {
        return interval;
    }

    public void setInterval(Duration interval) {
        this.interval = interval;
    }
}
