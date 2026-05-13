package net.ftgo.order.saga;

import io.eventuate.tram.commands.consumer.CommandHandlers;
import io.eventuate.tram.commands.consumer.CommandMessage;
import io.eventuate.tram.messaging.common.Message;
import io.eventuate.tram.sagas.participant.SagaCommandHandlersBuilder;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import net.ftgo.common.orderflow.events.OrderCancelled;
import net.ftgo.order.domain.Order;
import net.ftgo.order.messaging.DomainEventPublisher;
import net.ftgo.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import static io.eventuate.tram.commands.consumer.CommandHandlerReplyBuilder.withSuccess;

/**
 * Local saga participant for CancelOrderSaga.
 * 
 * Handles local steps that operate on the Order aggregate:
 * - beginCancel: Transitions order to CANCEL_PENDING state
 * - undoCancel: Compensation that restores order to APPROVED state
 * - confirmCancel: Final step that transitions order to CANCELLED state
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
public class CancelOrderSagaLocalSteps {
    
    private static final Logger logger = LoggerFactory.getLogger(CancelOrderSagaLocalSteps.class);
    
    private final OrderRepository orderRepository;
    private final DomainEventPublisher eventPublisher;
    private final Counter ordersCancelledCounter;
    private final Counter sagaFailuresCounter;
    
    public CancelOrderSagaLocalSteps(OrderRepository orderRepository,
                                    DomainEventPublisher eventPublisher,
                                    MeterRegistry meterRegistry) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
        
        // Initialize metrics counters
        this.ordersCancelledCounter = Counter.builder("order_service_cancelled_orders_total")
            .description("Total number of cancelled orders")
            .register(meterRegistry);
        
        this.sagaFailuresCounter = Counter.builder("order_service_saga_failures_total")
            .description("Total number of saga failures")
            .tag("saga", "CancelOrderSaga")
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
            .onMessage(BeginCancelCommand.class, this::beginCancel)
            .onMessage(UndoCancelCommand.class, this::undoCancel)
            .onMessage(ConfirmCancelCommand.class, this::confirmCancel)
            .build();
    }
    
    /**
     * Begins order cancellation (first saga step).
     * Transitions order from APPROVED to CANCEL_PENDING state.
     * 
     * This implements the semantic lock to prevent concurrent modifications
     * during saga execution.
     * 
     * @param cm the command message
     * @return success reply
     */
    @Transactional
    public Message beginCancel(CommandMessage<BeginCancelCommand> cm) {
        beginCancelOrder(cm.getCommand().getOrderId());
        return withSuccess();
    }

    @Transactional
    public void beginCancelOrder(Long orderId) {
        logger.info("Beginning order cancellation: orderId={}", orderId);
        
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
        
        // Transition to CANCEL_PENDING state (semantic lock)
        order.beginCancel();
        orderRepository.save(order);
        
        logger.info("Order cancellation initiated: orderId={}, state={}", orderId, order.getState());
    }
    
    /**
     * Undoes order cancellation (compensation for beginCancel).
     * Transitions order from CANCEL_PENDING back to APPROVED state.
     * 
     * This method is called when CancelOrderSaga fails before the pivot point.
     * It restores the order to its previous state.
     * 
     * @param cm the command message
     * @return success reply
     */
    @Transactional
    public Message undoCancel(CommandMessage<UndoCancelCommand> cm) {
        undoCancelOrder(cm.getCommand().getOrderId());
        return withSuccess();
    }

    @Transactional
    public void undoCancelOrder(Long orderId) {
        logger.warn("Undoing order cancellation due to saga failure: orderId={}", orderId);
        
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
        
        // Restore to APPROVED state
        order.undoCancel();
        orderRepository.save(order);
        
        // Increment saga failure counter
        sagaFailuresCounter.increment();
        
        logger.warn("Order cancellation undone: orderId={}, state={}", orderId, order.getState());
    }
    
    /**
     * Confirms order cancellation (final saga step).
     * Transitions order from CANCEL_PENDING to CANCELLED state.
     * 
     * This method is called when CancelOrderSaga completes successfully.
     * It publishes an OrderCancelled event and increments metrics counters.
     * 
     * @param cm the command message
     * @return success reply
     */
    @Transactional
    public Message confirmCancel(CommandMessage<ConfirmCancelCommand> cm) {
        ConfirmCancelCommand command = cm.getCommand();
        Long orderId = command.getOrderId();
        
        logger.info("Confirming order cancellation after successful saga: orderId={}", orderId);
        
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
        
        // Transition to CANCELLED state
        order.confirmCancel();
        orderRepository.save(order);
        
        // Publish OrderCancelled event via transactional outbox
        OrderCancelled event = new OrderCancelled(
            order.getId(),
            order.getConsumerId(),
            order.getRestaurantId()
        );
        eventPublisher.publishOrderEvent(order.getId(), event);
        
        // Increment metrics counter
        ordersCancelledCounter.increment();
        
        logger.info("Order cancelled: orderId={}, state={}", orderId, order.getState());
        
        return withSuccess();
    }
    
    /**
     * Command to begin order cancellation (local step).
     */
    public static class BeginCancelCommand implements io.eventuate.tram.commands.common.Command {
        private Long orderId;
        
        public BeginCancelCommand() {
        }
        
        public BeginCancelCommand(Long orderId) {
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
     * Command to undo order cancellation (local compensation).
     */
    public static class UndoCancelCommand implements io.eventuate.tram.commands.common.Command {
        private Long orderId;
        
        public UndoCancelCommand() {
        }
        
        public UndoCancelCommand(Long orderId) {
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
     * Command to confirm order cancellation (local step).
     */
    public static class ConfirmCancelCommand implements io.eventuate.tram.commands.common.Command {
        private Long orderId;
        
        public ConfirmCancelCommand() {
        }
        
        public ConfirmCancelCommand(Long orderId) {
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
