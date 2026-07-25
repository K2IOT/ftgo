package net.ftgo.common.messaging;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxMetricsConfigurationTest {

    @Test
    void createsNoopMetricsWhenJdbcOrMeterRegistryIsMissing() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(OutboxMetricsConfiguration.class);

            context.refresh();

            OutboxMetrics metrics = context.getBean(OutboxMetrics.class);
            assertThat(metrics.rowBacklog()).isZero();
            assertThat(metrics.oldestAgeSeconds()).isZero();
            assertThat(metrics.cleanupEligibleCount()).isZero();
        }
    }
}
