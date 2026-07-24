package net.ftgo.order.config;

import io.eventuate.tram.sagas.spring.participant.SagaParticipantConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import net.ftgo.order.messaging.DomainEventPublisher;
import net.ftgo.order.repository.OrderRepository;
import net.ftgo.order.saga.ReviseOrderSaga;
import net.ftgo.order.saga.ReviseOrderSagaLocalSteps;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Configuration for ReviseOrderSaga.
 *
 * Local Order commands are registered by CreateOrderSagaConfiguration in the
 * single consolidated orderSagaCommandDispatcher.
 */
@Configuration
@Import(SagaParticipantConfiguration.class)
public class ReviseOrderSagaConfiguration {

    @Bean
    public ReviseOrderSaga reviseOrderSaga(ReviseOrderSagaLocalSteps localSteps) {
        return new ReviseOrderSaga(localSteps);
    }

    @Bean
    public ReviseOrderSagaLocalSteps reviseOrderSagaLocalSteps(
        OrderRepository orderRepository,
        DomainEventPublisher eventPublisher,
        MeterRegistry meterRegistry
    ) {
        return new ReviseOrderSagaLocalSteps(orderRepository, eventPublisher, meterRegistry);
    }
}
