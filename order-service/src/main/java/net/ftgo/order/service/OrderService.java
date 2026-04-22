package net.ftgo.order.service;

import io.eventuate.tram.sagas.orchestration.SagaInstanceFactory;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import net.ftgo.common.Money;
import net.ftgo.order.domain.*;
import net.ftgo.order.repository.OrderRepository;
import net.ftgo.order.saga.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service class for Order operations.
 * 
 * Handles order creation, cancellation, and revision by:
 * 1. Creating/updating Order aggregates
 * 2. Initiating sagas for distributed transactions
 * 3. Enforcing semantic locks (pending states)
 * 4. Recording metrics
 */
@Service
public class OrderService {
    
    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);
    
    private final OrderRepository orderRepository;
    private final SagaInstanceFactory sagaInstanceFactory;
    private final CreateOrderSaga createOrderSaga;
    private final CancelOrderSaga cancelOrderSaga;
    private final ReviseOrderSaga reviseOrderSaga;
    private final Counter ordersPlacedCounter;
    
    public OrderService(OrderRepository orderRepository,
                       SagaInstanceFactory sagaInstanceFactory,
                       CreateOrderSaga createOrderSaga,
                       CancelOrderSaga cancelOrderSaga,
                       ReviseOrderSaga reviseOrderSaga,
                       MeterRegistry meterRegistry) {
        this.orderRepository = orderRepository;
        this.sagaInstanceFactory = sagaInstanceFactory;
        this.createOrderSaga = createOrderSaga;
        this.cancelOrderSaga = cancelOrderSaga;
        this.reviseOrderSaga = reviseOrderSaga;
        
        // Initialize metrics counter
        this.ordersPlacedCounter = Counter.builder("order_service_placed_orders_total")
            .description("Total number of orders placed")
            .register(meterRegistry);
    }
    
    /**
     * Creates a new order and initiates CreateOrderSaga.
     * 
     * Steps:
     * 1. Validate request parameters
     * 2. Create Order aggregate in APPROVAL_PENDING state
     * 3. Persist order to database
     * 4. Initiate CreateOrderSaga for distributed approval
     * 5. Increment metrics counter
     * 
     * @param consumerId the consumer placing the order
     * @param restaurantId the restaurant fulfilling the order
     * @param lineItems the order line items
     * @param deliveryInfo the delivery information
     * @param paymentInfo the payment information
     * @return the created order ID
     * @throws IllegalArgumentException if any parameter is invalid
     */
    @Transactional
    public Long createOrder(Long consumerId, Long restaurantId, List<OrderLineItem> lineItems,
                           DeliveryInfo deliveryInfo, PaymentInfo paymentInfo) {
        logger.info("Creating order: consumerId={}, restaurantId={}, lineItemCount={}",
            consumerId, restaurantId, lineItems.size());
        
        // Create Order aggregate in APPROVAL_PENDING state
        Order order = new Order(consumerId, restaurantId, lineItems, deliveryInfo, paymentInfo);
        
        // Persist order
        order = orderRepository.save(order);
        
        logger.info("Order created with id={}, state={}, total={}",
            order.getId(), order.getState(), order.getOrderTotal());
        
        // Create saga data
        CreateOrderSagaData sagaData = new CreateOrderSagaData(
            order.getId(),
            order.getConsumerId(),
            order.getRestaurantId(),
            order.getLineItems(),
            order.getOrderTotal()
        );
        
        // Initiate CreateOrderSaga
        sagaInstanceFactory.create(createOrderSaga, sagaData);
        
        logger.info("CreateOrderSaga initiated for orderId={}", order.getId());
        
        // Increment metrics counter
        ordersPlacedCounter.increment();
        
        return order.getId();
    }
    
    /**
     * Retrieves an order by ID.
     * 
     * @param orderId the order ID
     * @return the order
     * @throws OrderNotFoundException if order not found
     */
    @Transactional(readOnly = true)
    public Order getOrder(Long orderId) {
        logger.debug("Retrieving order: orderId={}", orderId);
        
        return orderRepository.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));
    }
    
    /**
     * Cancels an order and initiates CancelOrderSaga.
     * 
     * Steps:
     * 1. Retrieve order
     * 2. Validate order is in APPROVED state (semantic lock check)
     * 3. Transition order to CANCEL_PENDING state
     * 4. Persist order
     * 5. Initiate CancelOrderSaga for distributed cancellation
     * 
     * @param orderId the order ID to cancel
     * @throws OrderNotFoundException if order not found
     * @throws IllegalStateException if order is not in APPROVED state or is in pending state
     */
    @Transactional
    public void cancelOrder(Long orderId) {
        logger.info("Cancelling order: orderId={}", orderId);
        
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));
        
        // Validate order is not in a pending state (semantic lock)
        order.validateNotPending();
        
        // Transition to CANCEL_PENDING state
        order.beginCancel();
        orderRepository.save(order);
        
        logger.info("Order transitioned to CANCEL_PENDING: orderId={}", orderId);
        
        // Create saga data
        // Note: ticketId and authorizationId would need to be retrieved from saga instance
        // For now, we'll use placeholder values (to be enhanced with saga instance lookup)
        CancelOrderSagaData sagaData = new CancelOrderSagaData(
            order.getId(),
            null, // ticketId - to be retrieved from CreateOrderSaga instance
            null  // authorizationId - to be retrieved from CreateOrderSaga instance
        );
        
        // Initiate CancelOrderSaga
        sagaInstanceFactory.create(cancelOrderSaga, sagaData);
        
        logger.info("CancelOrderSaga initiated for orderId={}", orderId);
    }
    
    /**
     * Revises an order and initiates ReviseOrderSaga.
     * 
     * Steps:
     * 1. Retrieve order
     * 2. Validate order is in APPROVED state (semantic lock check)
     * 3. Transition order to REVISION_PENDING state
     * 4. Persist order
     * 5. Initiate ReviseOrderSaga for distributed revision
     * 
     * @param orderId the order ID to revise
     * @param revisedLineItems the new line items
     * @throws OrderNotFoundException if order not found
     * @throws IllegalStateException if order is not in APPROVED state or is in pending state
     * @throws IllegalArgumentException if revised line items are invalid
     */
    @Transactional
    public void reviseOrder(Long orderId, List<OrderLineItem> revisedLineItems) {
        logger.info("Revising order: orderId={}, revisedLineItemCount={}",
            orderId, revisedLineItems.size());
        
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));
        
        // Validate order is not in a pending state (semantic lock)
        order.validateNotPending();
        
        // Transition to REVISION_PENDING state
        order.beginRevise();
        orderRepository.save(order);
        
        logger.info("Order transitioned to REVISION_PENDING: orderId={}", orderId);
        
        // Calculate revised total
        Money revisedTotal = revisedLineItems.stream()
            .map(OrderLineItem::getTotal)
            .reduce(Money.ZERO, Money::add);
        
        // Create saga data
        // Note: ticketId and authorizationId would need to be retrieved from saga instance
        ReviseOrderSagaData sagaData = new ReviseOrderSagaData(
            order.getId(),
            revisedLineItems,
            revisedTotal,
            null, // ticketId - to be retrieved from CreateOrderSaga instance
            null  // authorizationId - to be retrieved from CreateOrderSaga instance
        );
        
        // Initiate ReviseOrderSaga
        sagaInstanceFactory.create(reviseOrderSaga, sagaData);
        
        logger.info("ReviseOrderSaga initiated for orderId={}, revisedTotal={}",
            orderId, revisedTotal);
    }
}
