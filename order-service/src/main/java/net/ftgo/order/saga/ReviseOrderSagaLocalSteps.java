package net.ftgo.order.saga;

import io.eventuate.tram.commands.consumer.CommandHandlers;
import io.eventuate.tram.commands.consumer.CommandMessage;
import io.eventuate.tram.messaging.common.Message;
import io.eventuate.tram.sagas.participant.SagaCommandHandlersBuilder;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import net.ftgo.common.channels.ChannelNames;
import net.ftgo.common.orderflow.events.OrderCreated;
import net.ftgo.common.orderflow.events.OrderRevised;
import net.ftgo.order.domain.Order;
import net.ftgo.order.domain.OrderLineItem;
import net.ftgo.order.messaging.DomainEventPublisher;
import net.ftgo.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static io.eventuate.tram.commands.consumer.CommandHandlerReplyBuilder.withSuccess;

/**
 * Local saga participant for ReviseOrderSaga.
 * 
 * Handles local steps that operate on the Order aggregate:
 * - beginRevise: Transitions order to REVISION_PENDING state
 * - undoRevise: Compensation that restores order to APPROVED state
 * - confirmRevise: Final step that updates order details and transitions to APPROVED state
 * 
 * These steps are executed locally within the Order Service and don't
 * involve remote service calls.
 * 
 * Responsibilities:
 * - Update Order aggregate state
 * - Update order line items with revised details
 * - Publish domain events via transactional outbox
 * - Increment metrics counters
 * - Log saga failures
 */
@Component
public class ReviseOrderSagaLocalSteps {
    
    private static final Logger logger = LoggerFactory.getLogger(ReviseOrderSagaLocalSteps.class);
    
    private final OrderRepository orderRepository;
    private final DomainEventPublisher eventPublisher;
    private final Counter ordersRevisedCounter;
    private final Counter sagaFailuresCounter;
    
    public ReviseOrderSagaLocalSteps(OrderRepository orderRepository,
                                    DomainEventPublisher eventPublisher,
                                    MeterRegistry meterRegistry) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
        
        // Initialize metrics counters
        this.ordersRevisedCounter = Counter.builder("order_service_revised_orders_total")
            .description("Total number of revised orders")
            .register(meterRegistry);
        
        this.sagaFailuresCounter = Counter.builder("order_service_saga_failures_total")
            .description("Total number of saga failures")
            .tag("saga", "ReviseOrderSaga")
            .register(meterRegistry);
    }
    
    /**
     * Defines command handlers for local saga steps.
     * 
     * @return command handlers for Order Service channel
     */
    public CommandHandlers commandHandlers() {
        return SagaCommandHandlersBuilder
            .fromChannel(ChannelNames.REVISE_ORDER_SAGA_COMMAND_CHANNEL)
            .onMessage(BeginReviseCommand.class, this::beginRevise)
            .onMessage(UndoReviseCommand.class, this::undoRevise)
            .onMessage(ConfirmReviseCommand.class, this::confirmRevise)
            .build();
    }
    
    /**
     * Begins order revision (first saga step).
     * Transitions order from APPROVED to REVISION_PENDING state.
     * 
     * This implements the semantic lock to prevent concurrent modifications
     * during saga execution.
     * 
     * @param cm the command message
     * @return success reply
     */
    @Transactional
    public Message beginRevise(CommandMessage<BeginReviseCommand> cm) {
        beginReviseOrder(cm.getCommand().getOrderId());
        return withSuccess();
    }

    @Transactional
    public void beginReviseOrder(Long orderId) {
        
        logger.info("Beginning order revision: orderId={}", orderId);
        
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
        
        // Transition to REVISION_PENDING state (semantic lock)
        order.beginRevise();
        orderRepository.save(order);
        
        logger.info("Order revision initiated: orderId={}, state={}", orderId, order.getState());
    }
    
    /**
     * Undoes order revision (compensation for beginRevise).
     * Transitions order from REVISION_PENDING back to APPROVED state.
     * 
     * This method is called when ReviseOrderSaga fails before the pivot point.
     * It restores the order to its previous state without changing line items.
     * 
     * @param cm the command message
     * @return success reply
     */
    @Transactional
    public Message undoRevise(CommandMessage<UndoReviseCommand> cm) {
        undoReviseOrder(cm.getCommand().getOrderId());
        return withSuccess();
    }

    @Transactional
    public void undoReviseOrder(Long orderId) {
        
        logger.warn("Undoing order revision due to saga failure: orderId={}", orderId);
        
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
        
        // Restore to APPROVED state (line items remain unchanged)
        order.undoRevise();
        orderRepository.save(order);
        
        // Increment saga failure counter
        sagaFailuresCounter.increment();
        
        logger.warn("Order revision undone: orderId={}, state={}", orderId, order.getState());
    }
    
    /**
     * Confirms order revision (final saga step).
     * Transitions order from REVISION_PENDING to APPROVED state and updates line items.
     * 
     * This method is called when ReviseOrderSaga completes successfully.
     * It publishes an OrderRevised event and increments metrics counters.
     * 
     * @param cm the command message
     * @return success reply
     */
    @Transactional
    public Message confirmRevise(CommandMessage<ConfirmReviseCommand> cm) {
        ConfirmReviseCommand command = cm.getCommand();
        Long orderId = command.getOrderId();
        List<OrderLineItem> revisedLineItems = command.getRevisedLineItems();
        Long authorizationId = command.getAuthorizationId();
        
        logger.info("Confirming order revision after successful saga: orderId={}", orderId);
        
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
        
        // Transition to APPROVED state and update line items
        order.confirmRevise(revisedLineItems);
        if (authorizationId != null) {
            order.setAuthorizationId(authorizationId);
        }
        orderRepository.save(order);
        
        // Publish OrderRevised event via transactional outbox
        OrderRevised event = new OrderRevised(
            order.getId(),
            order.getConsumerId(),
            order.getRestaurantId(),
            toEventLineItems(order.getLineItems()),
            order.getOrderTotal()
        );
        eventPublisher.publishOrderEvent(order.getId(), event);
        
        // Increment metrics counter
        ordersRevisedCounter.increment();
        
        logger.info("Order revised: orderId={}, state={}, newTotal={}", 
            orderId, order.getState(), order.getOrderTotal());
        
        return withSuccess();
    }

    private List<OrderCreated.LineItem> toEventLineItems(List<OrderLineItem> lineItems) {
        return lineItems.stream()
            .map(item -> new OrderCreated.LineItem(
                item.getMenuItemId(),
                item.getName(),
                item.getPrice(),
                item.getQuantity()
            ))
            .toList();
    }
    
    /**
     * Command to begin order revision (local step).
     */
    public static class BeginReviseCommand implements io.eventuate.tram.commands.common.Command {
        private Long orderId;
        
        public BeginReviseCommand() {
        }
        
        public BeginReviseCommand(Long orderId) {
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
     * Command to undo order revision (local compensation).
     */
    public static class UndoReviseCommand implements io.eventuate.tram.commands.common.Command {
        private Long orderId;
        
        public UndoReviseCommand() {
        }
        
        public UndoReviseCommand(Long orderId) {
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
     * Command to confirm order revision (local step).
     */
    public static class ConfirmReviseCommand implements io.eventuate.tram.commands.common.Command {
        private Long orderId;
        private List<OrderLineItem> revisedLineItems;
        private Long authorizationId;
        
        public ConfirmReviseCommand() {
        }
        
        public ConfirmReviseCommand(Long orderId, List<OrderLineItem> revisedLineItems) {
            this(orderId, revisedLineItems, null);
        }

        public ConfirmReviseCommand(Long orderId, List<OrderLineItem> revisedLineItems, Long authorizationId) {
            this.orderId = orderId;
            this.revisedLineItems = revisedLineItems;
            this.authorizationId = authorizationId;
        }
        
        public Long getOrderId() {
            return orderId;
        }
        
        public void setOrderId(Long orderId) {
            this.orderId = orderId;
        }
        
        public List<OrderLineItem> getRevisedLineItems() {
            return revisedLineItems;
        }
        
        public void setRevisedLineItems(List<OrderLineItem> revisedLineItems) {
            this.revisedLineItems = revisedLineItems;
        }

        public Long getAuthorizationId() {
            return authorizationId;
        }

        public void setAuthorizationId(Long authorizationId) {
            this.authorizationId = authorizationId;
        }
    }
}
