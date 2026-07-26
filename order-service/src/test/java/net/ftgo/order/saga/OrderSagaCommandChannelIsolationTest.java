package net.ftgo.order.saga;

import io.eventuate.tram.commands.consumer.CommandHandler;
import io.eventuate.tram.commands.consumer.CommandHandlers;
import io.eventuate.tram.commands.consumer.CommandWithDestination;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.ftgo.common.Money;
import net.ftgo.common.channels.ChannelNames;
import net.ftgo.order.domain.OrderLineItem;
import net.ftgo.order.messaging.DomainEventPublisher;
import net.ftgo.order.repository.OrderRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class OrderSagaCommandChannelIsolationTest {

    @Test
    void allLocalSagaCommandsShareOneCompleteDispatcherHandlerSet() {
        OrderRepository orderRepository = mock(OrderRepository.class);
        DomainEventPublisher eventPublisher = mock(DomainEventPublisher.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

        CreateOrderSagaLocalSteps createSteps = new CreateOrderSagaLocalSteps(
            orderRepository, eventPublisher, meterRegistry);
        CancelOrderSagaLocalSteps cancelSteps = new CancelOrderSagaLocalSteps(
            orderRepository, eventPublisher, meterRegistry);
        ReviseOrderSagaLocalSteps reviseSteps = new ReviseOrderSagaLocalSteps(
            orderRepository, eventPublisher, meterRegistry);
        CapturePaymentSagaLocalSteps capturePaymentSteps = mock(
            CapturePaymentSagaLocalSteps.class);

        CommandHandlers commandHandlers = new OrderSagaCommandHandlers(
            createSteps,
            cancelSteps,
            reviseSteps,
            capturePaymentSteps
        ).commandHandlers();
        List<CommandHandler> handlers = extractHandlers(commandHandlers);

        assertThat(handlers).hasSize(10);
        assertThat(handlers)
            .extracting(CommandHandler::getChannel)
            .containsOnly(ChannelNames.ORDER_SERVICE_COMMAND_CHANNEL);
    }

    @Test
    void orchestratorsSendLocalParticipantCommandsToTheConsolidatedChannel() {
        CreateOrderSagaData createData = new CreateOrderSagaData(
            101L, 301L, 202L,
            List.of(new OrderLineItem(11L, "Burger", new Money("25.00"), 1)),
            new Money("25.00")
        );
        CancelOrderSagaData cancelData = new CancelOrderSagaData(101L, 301L, 401L, 501L);
        ReviseOrderSagaData reviseData = new ReviseOrderSagaData(
            101L, 301L,
            List.of(new OrderLineItem(11L, "Burger", new Money("30.00"), 1)),
            new Money("30.00"), 401L, 501L, "revise-101"
        );

        assertThat(invokeCommand(new CreateOrderSaga(), "rejectOrder", CreateOrderSagaData.class, createData)
            .getDestinationChannel()).isEqualTo(ChannelNames.ORDER_SERVICE_COMMAND_CHANNEL);
        assertThat(invokeCommand(new CancelOrderSaga(), "confirmCancelStep", CancelOrderSagaData.class, cancelData)
            .getDestinationChannel()).isEqualTo(ChannelNames.ORDER_SERVICE_COMMAND_CHANNEL);
        assertThat(invokeCommand(new ReviseOrderSaga(), "confirmReviseStep", ReviseOrderSagaData.class, reviseData)
            .getDestinationChannel()).isEqualTo(ChannelNames.ORDER_SERVICE_COMMAND_CHANNEL);
    }

    private List<CommandHandler> extractHandlers(CommandHandlers handlers) {
        List<CommandHandler> result = new ArrayList<>();
        Class<?> type = handlers.getClass();
        while (type != null) {
            for (Field field : type.getDeclaredFields()) {
                field.setAccessible(true);
                try {
                    collectHandlers(field.get(handlers), result);
                } catch (IllegalAccessException e) {
                    throw new AssertionError("Unable to inspect Eventuate command handlers", e);
                }
            }
            type = type.getSuperclass();
        }
        return result;
    }

    private void collectHandlers(Object value, List<CommandHandler> handlers) {
        if (value instanceof CommandHandler handler) {
            handlers.add(handler);
        } else if (value instanceof Collection<?> collection) {
            collection.forEach(element -> collectHandlers(element, handlers));
        } else if (value instanceof Map<?, ?> map) {
            map.values().forEach(element -> collectHandlers(element, handlers));
        } else if (value instanceof Object[] array) {
            for (Object element : array) {
                collectHandlers(element, handlers);
            }
        }
    }

    private <T> CommandWithDestination invokeCommand(Object saga,
                                                     String methodName,
                                                     Class<T> dataType,
                                                     T data) {
        try {
            Method method = saga.getClass().getDeclaredMethod(methodName, dataType);
            method.setAccessible(true);
            return (CommandWithDestination) method.invoke(saga, data);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to inspect saga command destination", e);
        }
    }
}
