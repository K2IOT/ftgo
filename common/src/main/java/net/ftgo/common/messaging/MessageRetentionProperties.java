package net.ftgo.common.messaging;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationMin;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "ftgo.messaging.retention")
public class MessageRetentionProperties {

    private boolean enabled = false;

    @NotNull
    @DurationMin(days = 7)
    private Duration outboxRetention = Duration.ofDays(30);

    @NotNull
    @DurationMin(days = 7)
    private Duration completedCommandRetention = Duration.ofDays(30);

    @Min(1)
    @Max(500)
    private int batchSize = 500;

    @NotNull
    @DurationMin(seconds = 1)
    private Duration interval = Duration.ofMinutes(10);

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
