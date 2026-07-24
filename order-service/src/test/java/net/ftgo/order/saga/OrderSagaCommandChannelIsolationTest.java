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
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class OrderSagaCommandChannelIsolationTest {

    @Test
    void localSagaDispatchersSubscribeToDisjointChannels() {
        OrderRepository orderRepository = mock(OrderRepository.class);
        DomainEventPublisher eventPublisher = mock(DomainEventPublisher.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

        Set<String> createChannels = handlerChannels(new CreateOrderSagaLocalSteps(
            orderRepository, eventPublisher, meterRegistry).commandHandlers());
        Set<String> cancelChannels = handlerChannels(new CancelOrderSagaLocalSteps(
            orderRepository, eventPublisher, meterRegistry).commandHandlers());
        Set<String> reviseChannels = handlerChannels(new ReviseOrderSagaLocalSteps(
            orderRepository, eventPublisher, meterRegistry).commandHandlers());

        assertThat(createChannels).containsExactly(ChannelNames.CREATE_ORDER_SAGA_COMMAND_CHANNEL);
        assertThat(cancelChannels).containsExactly(ChannelNames.CANCEL_ORDER_SAGA_COMMAND_CHANNEL);
        assertThat(reviseChannels).containsExactly(ChannelNames.REVISE_ORDER_SAGA_COMMAND_CHANNEL);
        assertThat(Set.of(
            ChannelNames.CREATE_ORDER_SAGA_COMMAND_CHANNEL,
            ChannelNames.CANCEL_ORDER_SAGA_COMMAND_CHANNEL,
            ChannelNames.REVISE_ORDER_SAGA_COMMAND_CHANNEL
        )).hasSize(3);
    }

    @Test
    void orchestratorsSendLocalParticipantCommandsToTheirDedicatedChannels() {
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
            .getDestinationChannel()).isEqualTo(ChannelNames.CREATE_ORDER_SAGA_COMMAND_CHANNEL);
        assertThat(invokeCommand(new CancelOrderSaga(), "confirmCancelStep", CancelOrderSagaData.class, cancelData)
            .getDestinationChannel()).isEqualTo(ChannelNames.CANCEL_ORDER_SAGA_COMMAND_CHANNEL);
        assertThat(invokeCommand(new ReviseOrderSaga(), "confirmReviseStep", ReviseOrderSagaData.class, reviseData)
            .getDestinationChannel()).isEqualTo(ChannelNames.REVISE_ORDER_SAGA_COMMAND_CHANNEL);
    }

    private Set<String> handlerChannels(CommandHandlers handlers) {
        Set<String> channels = new HashSet<>();
        Class<?> type = handlers.getClass();
        while (type != null) {
            for (Field field : type.getDeclaredFields()) {
                field.setAccessible(true);
                try {
                    collectChannels(field.get(handlers), channels);
                } catch (IllegalAccessException e) {
                    throw new AssertionError("Unable to inspect Eventuate command handlers", e);
                }
            }
            type = type.getSuperclass();
        }
        return channels;
    }

    private void collectChannels(Object value, Set<String> channels) {
        if (value instanceof CommandHandler handler) {
            channels.add(handler.getChannel());
        } else if (value instanceof Collection<?> collection) {
            collection.forEach(element -> collectChannels(element, channels));
        } else if (value instanceof Map<?, ?> map) {
            map.values().forEach(element -> collectChannels(element, channels));
        } else if (value instanceof Object[] array) {
            for (Object element : array) {
                collectChannels(element, channels);
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
