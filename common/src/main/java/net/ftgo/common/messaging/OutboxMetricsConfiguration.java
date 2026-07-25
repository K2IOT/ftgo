package net.ftgo.common.messaging;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;

@Configuration
public class OutboxMetricsConfiguration {

    @Bean
    public OutboxMetrics outboxMetrics(
        ObjectProvider<JdbcTemplate> jdbcTemplateProvider,
        ObjectProvider<MeterRegistry> meterRegistryProvider,
        @Value("${spring.application.name:ftgo-service}") String serviceName,
        @Value("${ftgo.outbox.retention:PT168H}") String retention
    ) {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        MeterRegistry meterRegistry = meterRegistryProvider.getIfAvailable();
        if (jdbcTemplate == null || meterRegistry == null) {
            return OutboxMetrics.noop();
        }
        return new OutboxMetrics(serviceName, jdbcTemplate, meterRegistry, Duration.parse(retention));
    }
}
