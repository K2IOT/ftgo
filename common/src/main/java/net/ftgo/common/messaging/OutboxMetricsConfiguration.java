package net.ftgo.common.messaging;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(MessageRetentionProperties.class)
public class OutboxMetricsConfiguration {

    @Bean
    public OutboxMetrics outboxMetrics(
        ObjectProvider<JdbcTemplate> jdbcTemplateProvider,
        ObjectProvider<MeterRegistry> meterRegistryProvider,
        @Value("${spring.application.name:ftgo-service}") String serviceName,
        MessageRetentionProperties retentionProperties
    ) {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        MeterRegistry meterRegistry = meterRegistryProvider.getIfAvailable();
        if (jdbcTemplate == null || meterRegistry == null) {
            return OutboxMetrics.noop();
        }
        return new OutboxMetrics(
            serviceName,
            jdbcTemplate,
            meterRegistry,
            retentionProperties.getOutboxRetention()
        );
    }

    @Bean
    @ConditionalOnProperty(
        prefix = "ftgo.messaging.retention",
        name = "enabled",
        havingValue = "true"
    )
    public JdbcMessageRetentionWorker jdbcMessageRetentionWorker(
        JdbcTemplate jdbcTemplate,
        MeterRegistry meterRegistry,
        @Value("${spring.application.name:ftgo-service}") String serviceName,
        MessageRetentionProperties retentionProperties
    ) {
        return new JdbcMessageRetentionWorker(
            serviceName,
            jdbcTemplate,
            meterRegistry,
            retentionProperties
        );
    }
}
