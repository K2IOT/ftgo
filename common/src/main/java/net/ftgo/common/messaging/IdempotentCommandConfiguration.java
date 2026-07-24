package net.ftgo.common.messaging;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/** Shared Spring wiring for participant command result caching. */
@Configuration(proxyBeanMethods = false)
public class IdempotentCommandConfiguration {

    @Bean
    public ProcessedCommandStore processedCommandStore(JdbcTemplate jdbcTemplate) {
        return new JdbcProcessedCommandStore(jdbcTemplate);
    }

    @Bean
    public IdempotentCommandExecutor idempotentCommandExecutor(
        ProcessedCommandStore processedCommandStore
    ) {
        return new IdempotentCommandExecutor(processedCommandStore);
    }
}
