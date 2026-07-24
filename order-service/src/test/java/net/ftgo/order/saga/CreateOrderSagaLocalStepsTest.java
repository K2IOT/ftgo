package net.ftgo.order.saga;

import io.eventuate.tram.commands.consumer.CommandMessage;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.ftgo.common.Address;
import net.ftgo.common.Money;
import net.ftgo.common.orderflow.events.OrderApproved;
import net.ftgo.order.domain.DeliveryInfo;
import net.ftgo.order.domain.Order;
import net.ftgo.order.domain.OrderLineItem;
import net.ftgo.order.domain.OrderState;
import net.ftgo.order.domain.PaymentInfo;
import net.ftgo.order.messaging.DomainEventPublisher;
import net.ftgo.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateOrderSagaLocalStepsTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private DomainEventPublisher eventPublisher;

    @Mock
    private CommandMessage<CreateOrderSagaLocalSteps.ApproveOrderCommand> approveOrderCommandMessage;

    private CreateOrderSagaLocalSteps localSteps;

    @BeforeEach
    void setUp() {
        localSteps = new CreateOrderSagaLocalSteps(
            orderRepository,
            eventPublisher,
            new SimpleMeterRegistry()
        );
    }

    @Test
    void approveOrderPublishesSharedOrderApprovedWithAggregateVersion() {
        LocalDateTime deliveryTime = LocalDateTime.now().plusHours(2);
        Address deliveryAddress = new Address(
            "456 Consumer Ave",
            "San Francisco",
            "CA",
            "94103"
        );
        Order order = new Order(
            202L,
            303L,
            List.of(new OrderLineItem(10L, "Burger", new Money("12.99"), 2)),
            new DeliveryInfo(deliveryAddress, deliveryTime),
            new PaymentInfo("tok_test_123")
        );
        setPersistedIdentity(order, 101L, 0);
        var command = new CreateOrderSagaLocalSteps.ApproveOrderCommand(101L, 404L, 505L);

        when(approveOrderCommandMessage.getCommand()).thenReturn(command);
        when(orderRepository.findByIdWithLock(101L)).thenReturn(Optional.of(order));

        localSteps.approveOrder(approveOrderCommandMessage);

        assertEquals(OrderState.APPROVED, order.getState());
        verify(orderRepository).saveAndFlush(order);
        ArgumentCaptor<OrderApproved> eventCaptor = ArgumentCaptor.forClass(OrderApproved.class);
        verify(eventPublisher).publishOrderEvent(
            eq(101L),
            eq(order.getVersion().longValue()),
            eventCaptor.capture()
        );
        OrderApproved event = eventCaptor.getValue();
        assertEquals(101L, event.getOrderId());
        assertEquals(202L, event.getConsumerId());
        assertEquals(303L, event.getRestaurantId());
        assertEquals(new Money("25.98"), event.getOrderTotal());
        assertEquals(404L, event.getTicketId());
        assertEquals(505L, event.getAuthorizationId());
        assertEquals(deliveryAddress, event.getDeliveryAddress());
        assertEquals(deliveryTime, event.getDeliveryTime());
    }

    private void setPersistedIdentity(Order order, Long orderId, Integer version) {
        try {
            var idField = Order.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(order, orderId);
            var versionField = Order.class.getDeclaredField("version");
            versionField.setAccessible(true);
            versionField.set(order, version);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set persisted Order identity", e);
        }
    }
}
