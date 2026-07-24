package net.ftgo.order.config;

import io.eventuate.tram.sagas.spring.participant.SagaParticipantConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import net.ftgo.order.messaging.DomainEventPublisher;
import net.ftgo.order.repository.OrderRepository;
import net.ftgo.order.saga.CancelOrderSaga;
import net.ftgo.order.saga.CancelOrderSagaLocalSteps;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Configuration for CancelOrderSaga.
 *
 * Local Order commands are registered by CreateOrderSagaConfiguration in the
 * single consolidated orderSagaCommandDispatcher.
 */
@Configuration
@Import(SagaParticipantConfiguration.class)
public class CancelOrderSagaConfiguration {

    @Bean
    public CancelOrderSaga cancelOrderSaga(CancelOrderSagaLocalSteps localSteps) {
        return new CancelOrderSaga(localSteps);
    }

    @Bean
    public CancelOrderSagaLocalSteps cancelOrderSagaLocalSteps(
        OrderRepository orderRepository,
        DomainEventPublisher eventPublisher,
        MeterRegistry meterRegistry
    ) {
        return new CancelOrderSagaLocalSteps(orderRepository, eventPublisher, meterRegistry);
    }
}
