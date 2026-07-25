package net.ftgo.common.messaging;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;

@Configuration
public class OutboxMetricsConfiguration {

    @Bean
    public OutboxMetrics outboxMetrics(
        JdbcTemplate jdbcTemplate,
        MeterRegistry meterRegistry,
        @Value("${spring.application.name:ftgo-service}") String serviceName,
        @Value("${ftgo.outbox.retention:PT168H}") Duration retention
    ) {
        return new OutboxMetrics(serviceName, jdbcTemplate, meterRegistry, retention);
    }
}
