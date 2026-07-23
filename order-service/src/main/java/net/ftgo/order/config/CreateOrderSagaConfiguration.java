package net.ftgo.order.config;

import io.eventuate.tram.commands.consumer.CommandDispatcher;
import io.eventuate.tram.commands.consumer.CommandHandlers;
import io.eventuate.tram.sagas.participant.SagaCommandDispatcherFactory;
import io.eventuate.tram.sagas.spring.participant.SagaParticipantConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import net.ftgo.order.messaging.DomainEventPublisher;
import net.ftgo.order.repository.OrderRepository;
import net.ftgo.order.saga.CreateOrderSaga;
import net.ftgo.order.saga.CreateOrderSagaLocalSteps;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * CreateOrderSaga definition and its local compensation participant.
 */
@Configuration
@Import(SagaParticipantConfiguration.class)
public class CreateOrderSagaConfiguration {

    @Bean
    public CreateOrderSagaLocalSteps createOrderSagaLocalSteps(
        OrderRepository orderRepository,
        DomainEventPublisher eventPublisher,
        MeterRegistry meterRegistry
    ) {
        return new CreateOrderSagaLocalSteps(
            orderRepository,
            eventPublisher,
            meterRegistry
        );
    }

    @Bean
    public CreateOrderSaga createOrderSaga(CreateOrderSagaLocalSteps localSteps) {
        return new CreateOrderSaga(localSteps);
    }

    @Bean
    public CommandHandlers createOrderSagaCommandHandlers(CreateOrderSagaLocalSteps localSteps) {
        return localSteps.commandHandlers();
    }

    @Bean
    public CommandDispatcher createOrderSagaCommandDispatcher(
        SagaCommandDispatcherFactory sagaCommandDispatcherFactory,
        CommandHandlers createOrderSagaCommandHandlers
    ) {
        return sagaCommandDispatcherFactory.make(
            "createOrderSagaCommandDispatcher",
            createOrderSagaCommandHandlers
        );
    }
}
