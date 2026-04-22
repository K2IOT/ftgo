package net.ftgo.order.saga;

import io.eventuate.tram.commands.consumer.CommandHandlers;
import io.eventuate.tram.commands.consumer.CommandMessage;
import io.eventuate.tram.messaging.common.Message;
import io.eventuate.tram.sagas.participant.SagaCommandHandlersBuilder;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import net.ftgo.order.domain.Order;
import net.ftgo.order.domain.events.OrderApproved;
import net.ftgo.order.domain.events.OrderRejected;
import net.ftgo.order.messaging.DomainEventPublisher;
import net.ftgo.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import static io.eventuate.tram.commands.consumer.CommandHandlerReplyBuilder.withSuccess;

/**
 * Local saga participant for CreateOrderSaga.
 * 
 * Handles local steps that operate on the Order aggregate:
 * - rejectOrder: Compensation for createOrder step
 * - approveOrder: Final step to approve the order
 * 
 * These steps are executed locally within the Order Service and don't
 * involve remote service calls.
 * 
 * Responsibilities:
 * - Update Order aggregate state
 * - Publish domain events via transactional outbox
 * - Increment metrics counters
 * - Log saga failures
 */
@Component
public class CreateOrderSagaLocalSteps {
    
    private static final Logger logger = LoggerFactory.getLogger(CreateOrderSagaLocalSteps.class);
    
    private final OrderRepository orderRepository;
    private final DomainEventPublisher eventPublisher;
    private final Counter ordersApprovedCounter;
    private final Counter ordersRejectedCounter;
    private final Counter sagaFailuresCounter;
    
    public CreateOrderSagaLocalSteps(OrderRepository orderRepository,
                                    DomainEventPublisher eventPublisher,
                                    MeterRegistry meterRegistry) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
        
        // Initialize metrics counters
        this.ordersApprovedCounter = Counter.builder("order_service_approved_orders_total")
            .description("Total number of approved orders")
            .register(meterRegistry);
        
        this.ordersRejectedCounter = Counter.builder("order_service_rejected_orders_total")
            .description("Total number of rejected orders")
            .register(meterRegistry);
        
        this.sagaFailuresCounter = Counter.builder("order_service_saga_failures_total")
            .description("Total number of saga failures")
            .tag("saga", "CreateOrderSaga")
            .register(meterRegistry);
    }
    
    /**
     * Defines command handlers for local saga steps.
     * 
     * @return command handlers for Order Service channel
     */
    public CommandHandlers commandHandlers() {
        return SagaCommandHandlersBuilder
            .fromChannel("orderService")
            .onMessage(RejectOrderCommand.class, this::rejectOrder)
            .onMessage(ApproveOrderCommand.class, this::approveOrder)
            .build();
    }
    
    /**
     * Rejects an order (compensation for createOrder step).
     * Transitions order from APPROVAL_PENDING to REJECTED state.
     * 
     * This method is called when CreateOrderSaga fails before the pivot point.
     * It publishes an OrderRejected event and increments metrics counters.
     * 
     * @param cm the command message
     * @return success reply
     */
    @Transactional
    public Message rejectOrder(CommandMessage<RejectOrderCommand> cm) {
        RejectOrderCommand command = cm.getCommand();
        Long orderId = command.getOrderId();
        
        logger.warn("Rejecting order due to saga failure: orderId={}", orderId);
        
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
        
        // Transition to REJECTED state
        order.reject();
        orderRepository.save(order);
        
        // Publish OrderRejected event via transactional outbox
        OrderRejected event = new OrderRejected(
            order.getId(),
            order.getConsumerId(),
            order.getRestaurantId(),
            "Saga failed before pivot point"
        );
        eventPublisher.publishOrderEvent(order.getId(), event);
        
        // Increment metrics counters
        ordersRejectedCounter.increment();
        sagaFailuresCounter.increment();
        
        logger.warn("Order rejected: orderId={}, state={}", orderId, order.getState());
        
        return withSuccess();
    }
    
    /**
     * Approves an order (final saga step).
     * Transitions order from APPROVAL_PENDING to APPROVED state.
     * 
     * This method is called when CreateOrderSaga completes successfully.
     * It publishes an OrderApproved event and increments metrics counters.
     * 
     * @param cm the command message
     * @return success reply
     */
    @Transactional
    public Message approveOrder(CommandMessage<ApproveOrderCommand> cm) {
        ApproveOrderCommand command = cm.getCommand();
        Long orderId = command.getOrderId();
        
        logger.info("Approving order after successful saga: orderId={}", orderId);
        
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
        
        // Transition to APPROVED state
        order.approve();
        orderRepository.save(order);
        
        // Publish OrderApproved event via transactional outbox
        // Note: ticketId and authorizationId would be retrieved from saga data
        // For now, we'll publish with null values (to be enhanced when saga manager is integrated)
        OrderApproved event = new OrderApproved(
            order.getId(),
            order.getConsumerId(),
            order.getRestaurantId(),
            order.getOrderTotal(),
            null, // ticketId from saga data
            null  // authorizationId from saga data
        );
        eventPublisher.publishOrderEvent(order.getId(), event);
        
        // Increment metrics counter
        ordersApprovedCounter.increment();
        
        logger.info("Order approved: orderId={}, state={}", orderId, order.getState());
        
        return withSuccess();
    }
    
    /**
     * Command to reject an order (local compensation).
     */
    public static class RejectOrderCommand implements io.eventuate.tram.commands.common.Command {
        private Long orderId;
        
        public RejectOrderCommand() {
        }
        
        public RejectOrderCommand(Long orderId) {
            this.orderId = orderId;
        }
        
        public Long getOrderId() {
            return orderId;
        }
        
        public void setOrderId(Long orderId) {
            this.orderId = orderId;
        }
    }
    
    /**
     * Command to approve an order (local step).
     */
    public static class ApproveOrderCommand implements io.eventuate.tram.commands.common.Command {
        private Long orderId;
        
        public ApproveOrderCommand() {
        }
        
        public ApproveOrderCommand(Long orderId) {
            this.orderId = orderId;
        }
        
        public Long getOrderId() {
            return orderId;
        }
        
        public void setOrderId(Long orderId) {
            this.orderId = orderId;
        }
    }
}
