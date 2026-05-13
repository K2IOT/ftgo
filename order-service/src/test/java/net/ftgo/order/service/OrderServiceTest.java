package net.ftgo.order.service;

import io.eventuate.tram.sagas.orchestration.SagaInstanceFactory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.ftgo.common.Money;
import net.ftgo.common.orderflow.events.OrderCreated;
import net.ftgo.order.domain.DeliveryInfo;
import net.ftgo.order.domain.Order;
import net.ftgo.order.domain.OrderLineItem;
import net.ftgo.order.domain.PaymentInfo;
import net.ftgo.order.messaging.DomainEventPublisher;
import net.ftgo.order.repository.OrderRepository;
import net.ftgo.order.saga.CancelOrderSaga;
import net.ftgo.order.saga.CancelOrderSagaData;
import net.ftgo.order.saga.CreateOrderSaga;
import net.ftgo.order.saga.CreateOrderSagaData;
import net.ftgo.order.saga.ReviseOrderSaga;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private DomainEventPublisher eventPublisher;

    @Mock
    private SagaInstanceFactory sagaInstanceFactory;

    @Mock
    private CreateOrderSaga createOrderSaga;

    @Mock
    private CancelOrderSaga cancelOrderSaga;

    @Mock
    private ReviseOrderSaga reviseOrderSaga;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(
            orderRepository,
            sagaInstanceFactory,
            createOrderSaga,
            cancelOrderSaga,
            reviseOrderSaga,
            eventPublisher,
            new SimpleMeterRegistry()
        );
    }

    @Test
    void createOrderPublishesOrderCreatedBeforeStartingCreateOrderSaga() {
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            setOrderId(order, 123L);
            return order;
        });

        List<OrderLineItem> lineItems = List.of(
            new OrderLineItem(10L, "Burger", new Money("12.99"), 2),
            new OrderLineItem(11L, "Fries", new Money("4.99"), 1)
        );
        DeliveryInfo deliveryInfo = new DeliveryInfo("123 Main St", LocalDateTime.now().plusHours(2));
        PaymentInfo paymentInfo = new PaymentInfo("tok_test_123");

        Long orderId = orderService.createOrder(456L, 789L, lineItems, deliveryInfo, paymentInfo);

        assertEquals(123L, orderId);

        ArgumentCaptor<OrderCreated> eventCaptor = ArgumentCaptor.forClass(OrderCreated.class);
        InOrder inOrder = inOrder(orderRepository, eventPublisher, sagaInstanceFactory);
        inOrder.verify(orderRepository).save(any(Order.class));
        inOrder.verify(eventPublisher).publishOrderEvent(eq(123L), eventCaptor.capture());
        inOrder.verify(sagaInstanceFactory).create(eq(createOrderSaga), any(CreateOrderSagaData.class));

        OrderCreated event = eventCaptor.getValue();
        assertEquals(123L, event.getOrderId());
        assertEquals(456L, event.getConsumerId());
        assertEquals(789L, event.getRestaurantId());
        assertEquals("APPROVAL_PENDING", event.getStatus());
        assertEquals(new BigDecimal("30.97"), event.getOrderTotal().getAmount());
        assertEquals("123 Main St", event.getDeliveryAddress());
        assertEquals(deliveryInfo.getDeliveryTime(), event.getDeliveryTime());
        assertEquals(2, event.getLineItems().size());
        assertEquals(10L, event.getLineItems().get(0).getMenuItemId());
        assertEquals("Burger", event.getLineItems().get(0).getName());
        assertEquals(new BigDecimal("12.99"), event.getLineItems().get(0).getPrice().getAmount());
        assertEquals(2, event.getLineItems().get(0).getQuantity());
    }

    @Test
    void cancelOrderUsesCurrentAuthorizationReference() {
        Order order = new Order(
            456L,
            789L,
            List.of(new OrderLineItem(10L, "Burger", new Money("12.99"), 2)),
            new DeliveryInfo("123 Main St", LocalDateTime.now().plusHours(2)),
            new PaymentInfo("tok_test_123")
        );
        setOrderId(order, 123L);
        order.approve();
        order.setTicketId(999L);
        order.setAuthorizationId(456L);

        when(orderRepository.findById(123L)).thenReturn(Optional.of(order));

        orderService.cancelOrder(123L);

        ArgumentCaptor<CancelOrderSagaData> dataCaptor = ArgumentCaptor.forClass(CancelOrderSagaData.class);
        verify(sagaInstanceFactory).create(eq(cancelOrderSaga), dataCaptor.capture());
        assertEquals(456L, dataCaptor.getValue().getAuthorizationId());
    }

    private void setOrderId(Order order, Long orderId) {
        try {
            var idField = Order.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(order, orderId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set order id", e);
        }
    }
}
