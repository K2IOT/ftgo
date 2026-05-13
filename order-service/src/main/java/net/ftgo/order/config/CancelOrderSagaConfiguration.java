package net.ftgo.order.config;

import io.eventuate.tram.commands.consumer.CommandDispatcher;
import io.eventuate.tram.commands.consumer.CommandHandlers;
import io.eventuate.tram.sagas.participant.SagaCommandDispatcherFactory;
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
 * Registers the saga definition and local saga participant as Spring beans.
 * The saga orchestrator will use these beans to execute the saga.
 * 
 * This configuration enables:
 * - CancelOrderSaga orchestration across Order, Kitchen, and Accounting services
 * - Local command handlers for order state transitions
 * - Compensation logic for saga failures before pivot point
 * - Retry logic for saga failures after pivot point
 * - Metrics collection for cancelled orders and saga failures
 */
@Configuration
@Import(SagaParticipantConfiguration.class)
public class CancelOrderSagaConfiguration {
    
    /**
     * Creates the CancelOrderSaga bean.
     * 
     * @return the CancelOrderSaga instance
     */
    @Bean
    public CancelOrderSaga cancelOrderSaga(CancelOrderSagaLocalSteps localSteps) {
        return new CancelOrderSaga(localSteps);
    }
    
    /**
     * Creates the local saga participant for CancelOrderSaga.
     * 
     * @param orderRepository the order repository
     * @param eventPublisher the domain event publisher
     * @param meterRegistry the metrics registry
     * @return the CancelOrderSagaLocalSteps instance
     */
    @Bean
    public CancelOrderSagaLocalSteps cancelOrderSagaLocalSteps(
            OrderRepository orderRepository,
            DomainEventPublisher eventPublisher,
            MeterRegistry meterRegistry) {
        return new CancelOrderSagaLocalSteps(orderRepository, eventPublisher, meterRegistry);
    }

    @Bean
    public CommandHandlers cancelOrderSagaCommandHandlers(CancelOrderSagaLocalSteps localSteps) {
        return localSteps.commandHandlers();
    }

    @Bean
    public CommandDispatcher cancelOrderSagaCommandDispatcher(
            SagaCommandDispatcherFactory sagaCommandDispatcherFactory,
            CommandHandlers cancelOrderSagaCommandHandlers) {
        return sagaCommandDispatcherFactory.make(
            "cancelOrderSagaCommandDispatcher",
            cancelOrderSagaCommandHandlers
        );
    }
}
