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

/** Local saga participant for ReviseOrderSaga. */
@Component
public class ReviseOrderSagaLocalSteps {

    private static final Logger logger = LoggerFactory.getLogger(ReviseOrderSagaLocalSteps.class);

    private final OrderRepository orderRepository;
    private final DomainEventPublisher eventPublisher;
    private final Counter ordersRevisedCounter;
    private final Counter sagaFailuresCounter;

    public ReviseOrderSagaLocalSteps(
        OrderRepository orderRepository,
        DomainEventPublisher eventPublisher,
        MeterRegistry meterRegistry
    ) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
        this.ordersRevisedCounter = Counter.builder("order_service_revised_orders_total")
            .description("Total number of revised orders")
            .register(meterRegistry);
        this.sagaFailuresCounter = Counter.builder("order_service_saga_failures_total")
            .description("Total number of saga failures")
            .tag("saga", "ReviseOrderSaga")
            .register(meterRegistry);
    }

    public CommandHandlers commandHandlers() {
        return SagaCommandHandlersBuilder
            .fromChannel(ChannelNames.REVISE_ORDER_SAGA_COMMAND_CHANNEL)
            .onMessage(BeginReviseCommand.class, this::beginRevise)
            .onMessage(UndoReviseCommand.class, this::undoRevise)
            .onMessage(ConfirmReviseCommand.class, this::confirmRevise)
            .build();
    }

    @Transactional
    public Message beginRevise(CommandMessage<BeginReviseCommand> cm) {
        beginReviseOrder(cm.getCommand().getOrderId());
        return withSuccess();
    }

    @Transactional
    public void beginReviseOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
        order.beginRevise();
        orderRepository.save(order);
    }

    @Transactional
    public Message undoRevise(CommandMessage<UndoReviseCommand> cm) {
        undoReviseOrder(cm.getCommand().getOrderId());
        return withSuccess();
    }

    @Transactional
    public void undoReviseOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
        order.undoRevise();
        orderRepository.save(order);
        sagaFailuresCounter.increment();
    }

    @Transactional
    public Message confirmRevise(CommandMessage<ConfirmReviseCommand> cm) {
        ConfirmReviseCommand command = cm.getCommand();
        Long orderId = command.getOrderId();
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        order.confirmRevise(command.getRevisedLineItems());
        if (command.getAuthorizationId() != null) {
            order.setAuthorizationId(command.getAuthorizationId());
        }
        orderRepository.saveAndFlush(order);

        eventPublisher.publishOrderEvent(
            order.getId(),
            order.getVersion().longValue(),
            new OrderRevised(
                order.getId(),
                order.getConsumerId(),
                order.getRestaurantId(),
                toEventLineItems(order.getLineItems()),
                order.getOrderTotal()
            )
        );
        ordersRevisedCounter.increment();
        logger.info("Order revised: orderId={}, state={}, newTotal={}, version={}",
            orderId, order.getState(), order.getOrderTotal(), order.getVersion());
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

    public static class BeginReviseCommand implements io.eventuate.tram.commands.common.Command {
        private Long orderId;

        public BeginReviseCommand() {
        }

        public BeginReviseCommand(Long orderId) { this.orderId = orderId; }
        public Long getOrderId() { return orderId; }
        public void setOrderId(Long orderId) { this.orderId = orderId; }
    }

    public static class UndoReviseCommand implements io.eventuate.tram.commands.common.Command {
        private Long orderId;

        public UndoReviseCommand() {
        }

        public UndoReviseCommand(Long orderId) { this.orderId = orderId; }
        public Long getOrderId() { return orderId; }
        public void setOrderId(Long orderId) { this.orderId = orderId; }
    }

    public static class ConfirmReviseCommand implements io.eventuate.tram.commands.common.Command {
        private Long orderId;
        private List<OrderLineItem> revisedLineItems;
        private Long authorizationId;

        public ConfirmReviseCommand() {
        }

        public ConfirmReviseCommand(Long orderId, List<OrderLineItem> revisedLineItems) {
            this(orderId, revisedLineItems, null);
        }

        public ConfirmReviseCommand(
            Long orderId,
            List<OrderLineItem> revisedLineItems,
            Long authorizationId
        ) {
            this.orderId = orderId;
            this.revisedLineItems = revisedLineItems;
            this.authorizationId = authorizationId;
        }

        public Long getOrderId() { return orderId; }
        public void setOrderId(Long orderId) { this.orderId = orderId; }
        public List<OrderLineItem> getRevisedLineItems() { return revisedLineItems; }
        public void setRevisedLineItems(List<OrderLineItem> revisedLineItems) {
            this.revisedLineItems = revisedLineItems;
        }
        public Long getAuthorizationId() { return authorizationId; }
        public void setAuthorizationId(Long authorizationId) { this.authorizationId = authorizationId; }
    }
}
