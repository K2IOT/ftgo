package net.ftgo.order.config;

import io.eventuate.tram.commands.consumer.CommandDispatcher;
import io.eventuate.tram.commands.consumer.CommandHandlers;
import io.eventuate.tram.sagas.participant.SagaCommandDispatcherFactory;
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
 * Registers the saga definition as a Spring bean.
 * The saga orchestrator will use this bean to execute the saga.
 */
@Configuration
@Import(SagaParticipantConfiguration.class)
public class ReviseOrderSagaConfiguration {
    
    /**
     * Creates the ReviseOrderSaga bean.
     * 
     * @return the ReviseOrderSaga instance
     */
    @Bean
    public ReviseOrderSaga reviseOrderSaga(ReviseOrderSagaLocalSteps localSteps) {
        return new ReviseOrderSaga(localSteps);
    }

    @Bean
    public ReviseOrderSagaLocalSteps reviseOrderSagaLocalSteps(
            OrderRepository orderRepository,
            DomainEventPublisher eventPublisher,
            MeterRegistry meterRegistry) {
        return new ReviseOrderSagaLocalSteps(orderRepository, eventPublisher, meterRegistry);
    }

    @Bean
    public CommandHandlers reviseOrderSagaCommandHandlers(ReviseOrderSagaLocalSteps localSteps) {
        return localSteps.commandHandlers();
    }

    @Bean
    public CommandDispatcher reviseOrderSagaCommandDispatcher(
            SagaCommandDispatcherFactory sagaCommandDispatcherFactory,
            CommandHandlers reviseOrderSagaCommandHandlers) {
        return sagaCommandDispatcherFactory.make(
            "reviseOrderSagaCommandDispatcher",
            reviseOrderSagaCommandHandlers
        );
    }
}
