package net.ftgo.order.service;

import io.eventuate.tram.sagas.orchestration.SagaInstanceFactory;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
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
import net.ftgo.order.saga.ReviseOrderSagaData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Application service for order lifecycle operations.
 */
@Service
public class OrderService {

    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final SagaInstanceFactory sagaInstanceFactory;
    private final CreateOrderSaga createOrderSaga;
    private final CancelOrderSaga cancelOrderSaga;
    private final ReviseOrderSaga reviseOrderSaga;
    private final DomainEventPublisher eventPublisher;
    private final Counter ordersPlacedCounter;

    public OrderService(
        OrderRepository orderRepository,
        SagaInstanceFactory sagaInstanceFactory,
        CreateOrderSaga createOrderSaga,
        CancelOrderSaga cancelOrderSaga,
        ReviseOrderSaga reviseOrderSaga,
        DomainEventPublisher eventPublisher,
        MeterRegistry meterRegistry
    ) {
        this.orderRepository = orderRepository;
        this.sagaInstanceFactory = sagaInstanceFactory;
        this.createOrderSaga = createOrderSaga;
        this.cancelOrderSaga = cancelOrderSaga;
        this.reviseOrderSaga = reviseOrderSaga;
        this.eventPublisher = eventPublisher;
        this.ordersPlacedCounter = Counter.builder("order_service_placed_orders_total")
            .description("Total number of orders placed")
            .register(meterRegistry);
    }

    /**
     * Backward-compatible entrypoint for callers that predate menu versioning.
     */
    @Transactional
    public Long createOrder(
        Long consumerId,
        Long restaurantId,
        List<OrderLineItem> lineItems,
        DeliveryInfo deliveryInfo,
        PaymentInfo paymentInfo
    ) {
        return createOrder(
            consumerId,
            restaurantId,
            0L,
            lineItems,
            deliveryInfo,
            paymentInfo
        );
    }

    /**
     * Persists the order and starts resource preparation against the exact menu
     * snapshot rendered by the client.
     */
    @Transactional
    public Long createOrder(
        Long consumerId,
        Long restaurantId,
        Long expectedMenuVersion,
        List<OrderLineItem> lineItems,
        DeliveryInfo deliveryInfo,
        PaymentInfo paymentInfo
    ) {
        logger.info(
            "Creating order: consumerId={}, restaurantId={}, expectedMenuVersion={}, lineItemCount={}",
            consumerId,
            restaurantId,
            expectedMenuVersion,
            lineItems.size()
        );

        Order order = orderRepository.save(new Order(
            consumerId,
            restaurantId,
            lineItems,
            deliveryInfo,
            paymentInfo
        ));

        eventPublisher.publishOrderEvent(order.getId(), toOrderCreated(order));
        sagaInstanceFactory.create(createOrderSaga, new CreateOrderSagaData(
            order.getId(),
            order.getConsumerId(),
            order.getRestaurantId(),
            order.getLineItems(),
            order.getOrderTotal(),
            expectedMenuVersion,
            order.getPaymentInfo().getPaymentToken()
        ));

        ordersPlacedCounter.increment();
        logger.info(
            "CreateOrderSaga initiated: orderId={}, state={}, total={}",
            order.getId(),
            order.getState(),
            order.getOrderTotal()
        );
        return order.getId();
    }

    private OrderCreated toOrderCreated(Order order) {
        List<OrderCreated.LineItem> lineItems = order.getLineItems().stream()
            .map(item -> new OrderCreated.LineItem(
                item.getMenuItemId(),
                item.getName(),
                item.getPrice(),
                item.getQuantity()
            ))
            .collect(Collectors.toList());

        return new OrderCreated(
            order.getId(),
            order.getConsumerId(),
            order.getRestaurantId(),
            order.getState().name(),
            order.getOrderTotal(),
            lineItems,
            order.getDeliveryInfo().getDeliveryAddress(),
            order.getDeliveryInfo().getDeliveryTime(),
            order.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public Order getOrder(Long orderId) {
        return orderRepository.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));
    }

    @Transactional
    public void cancelOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));

        sagaInstanceFactory.create(cancelOrderSaga, new CancelOrderSagaData(
            order.getId(),
            order.getConsumerId(),
            order.getTicketId(),
            order.getAuthorizationId()
        ));
        logger.info("CancelOrderSaga initiated for orderId={}", orderId);
    }

    @Transactional
    public void reviseOrder(Long orderId, List<OrderLineItem> revisedLineItems) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));

        Money revisedTotal = revisedLineItems.stream()
            .map(OrderLineItem::getTotal)
            .reduce(Money.ZERO, Money::add);

        sagaInstanceFactory.create(reviseOrderSaga, new ReviseOrderSagaData(
            order.getId(),
            order.getConsumerId(),
            revisedLineItems,
            revisedTotal,
            order.getTicketId(),
            order.getAuthorizationId(),
            paymentRevisionRequestId(order, revisedLineItems, revisedTotal)
        ));
        logger.info(
            "ReviseOrderSaga initiated for orderId={}, revisedTotal={}",
            orderId,
            revisedTotal
        );
    }

    private String paymentRevisionRequestId(
        Order order,
        List<OrderLineItem> revisedLineItems,
        Money revisedTotal
    ) {
        String itemsSignature = revisedLineItems.stream()
            .map(item -> item.getMenuItemId() + ":" + item.getQuantity())
            .collect(Collectors.joining("|"));
        return "revise-auth-" + order.getId() + "-" + order.getAuthorizationId() + "-"
            + revisedTotal.getAmount() + "-" + itemsSignature;
    }
}
