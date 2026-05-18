package net.ftgo.order.saga;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.ftgo.common.Money;
import net.ftgo.order.domain.DeliveryInfo;
import net.ftgo.order.domain.Order;
import net.ftgo.order.domain.OrderLineItem;
import net.ftgo.order.domain.OrderState;
import net.ftgo.order.domain.PaymentInfo;
import net.ftgo.order.messaging.DomainEventPublisher;
import net.ftgo.order.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CancelOrderSagaLocalStepsContractTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private DomainEventPublisher eventPublisher;

    @Test
    void undoCancelCompensationRestoresApprovedStateWithoutPublishingOrderEvent() {
        CancelOrderSagaLocalSteps localSteps = new CancelOrderSagaLocalSteps(
            orderRepository,
            eventPublisher,
            new SimpleMeterRegistry()
        );

        Order order = new Order(
            202L,
            303L,
            List.of(new OrderLineItem(10L, "Burger", new Money("12.99"), 2)),
            new DeliveryInfo("456 Consumer Ave", LocalDateTime.now().plusHours(2)),
            new PaymentInfo("tok_test_123")
        );
        setOrderId(order, 101L);
        order.approve();
        order.beginCancel();
        assertEquals(OrderState.CANCEL_PENDING, order.getState());

        when(orderRepository.findById(101L)).thenReturn(Optional.of(order));

        localSteps.undoCancelOrder(101L);

        assertEquals(OrderState.APPROVED, order.getState());
        verify(eventPublisher, never()).publishOrderEvent(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any());
    }

    private void setOrderId(Order order, Long orderId) {
        try {
            var idField = Order.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(order, orderId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set order ID", e);
        }
    }
}
