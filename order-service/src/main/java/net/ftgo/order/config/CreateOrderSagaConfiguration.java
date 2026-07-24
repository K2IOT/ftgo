package net.ftgo.order.config;

import io.eventuate.tram.commands.consumer.CommandDispatcher;
import io.eventuate.tram.commands.consumer.CommandHandlers;
import io.eventuate.tram.sagas.participant.SagaCommandDispatcherFactory;
import io.eventuate.tram.sagas.spring.participant.SagaParticipantConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import net.ftgo.order.messaging.DomainEventPublisher;
import net.ftgo.order.repository.OrderRepository;
import net.ftgo.order.saga.CancelOrderSagaLocalSteps;
import net.ftgo.order.saga.CreateOrderSaga;
import net.ftgo.order.saga.CreateOrderSagaLocalSteps;
import net.ftgo.order.saga.OrderSagaCommandHandlers;
import net.ftgo.order.saga.ReviseOrderSagaLocalSteps;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * CreateOrderSaga definition and the consolidated local Order saga participant.
 */
@Configuration
@Import(SagaParticipantConfiguration.class)
public class CreateOrderSagaConfiguration {

    @Bean
    public CreateOrderSagaLocalSteps createOrderSagaLocalSteps(
        OrderRepository orderRepository,
        DomainEventPublisher eventPublisher,
        MeterRegistry meterRegistry,
        PlatformTransactionManager transactionManager
    ) {
        return new CreateOrderSagaLocalSteps(
            orderRepository,
            eventPublisher,
            meterRegistry,
            transactionManager
        );
    }

    @Bean
    public CreateOrderSaga createOrderSaga(CreateOrderSagaLocalSteps localSteps) {
        return new CreateOrderSaga(localSteps);
    }

    @Bean
    public OrderSagaCommandHandlers orderSagaCommandHandlerRegistry(
        CreateOrderSagaLocalSteps createOrderSteps,
        CancelOrderSagaLocalSteps cancelOrderSteps,
        ReviseOrderSagaLocalSteps reviseOrderSteps
    ) {
        return new OrderSagaCommandHandlers(createOrderSteps, cancelOrderSteps, reviseOrderSteps);
    }

    @Bean(name = {
        "orderSagaCommandHandlers",
        "createOrderSagaCommandHandlers",
        "cancelOrderSagaCommandHandlers",
        "reviseOrderSagaCommandHandlers"
    })
    public CommandHandlers orderSagaCommandHandlers(
        OrderSagaCommandHandlers handlerRegistry
    ) {
        return handlerRegistry.commandHandlers();
    }

    @Bean(name = {
        "orderSagaCommandDispatcher",
        "createOrderSagaCommandDispatcher",
        "cancelOrderSagaCommandDispatcher",
        "reviseOrderSagaCommandDispatcher"
    })
    public CommandDispatcher orderSagaCommandDispatcher(
        SagaCommandDispatcherFactory sagaCommandDispatcherFactory,
        @Qualifier("orderSagaCommandHandlers") CommandHandlers commandHandlers
    ) {
        return sagaCommandDispatcherFactory.make(
            "orderSagaCommandDispatcher",
            commandHandlers
        );
    }
}
