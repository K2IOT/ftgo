package net.ftgo.order.config;

import net.ftgo.order.saga.ReviseOrderSaga;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for ReviseOrderSaga.
 * 
 * Registers the saga definition as a Spring bean.
 * The saga orchestrator will use this bean to execute the saga.
 */
@Configuration
public class ReviseOrderSagaConfiguration {
    
    /**
     * Creates the ReviseOrderSaga bean.
     * 
     * @return the ReviseOrderSaga instance
     */
    @Bean
    public ReviseOrderSaga reviseOrderSaga() {
        return new ReviseOrderSaga();
    }
}
