package net.ftgo.order.saga;

import io.eventuate.tram.commands.common.Command;
import io.eventuate.tram.commands.consumer.CommandHandlers;
import io.eventuate.tram.commands.consumer.CommandMessage;
import io.eventuate.tram.messaging.common.Message;
import io.eventuate.tram.sagas.participant.SagaCommandHandlersBuilder;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import net.ftgo.common.channels.ChannelNames;
import net.ftgo.common.orderflow.events.OrderCancelled;
import net.ftgo.order.domain.Order;
import net.ftgo.order.domain.OrderPaymentState;
import net.ftgo.order.messaging.DomainEventPublisher;
import net.ftgo.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import static io.eventuate.tram.commands.consumer.CommandHandlerReplyBuilder.withFailure;
import static io.eventuate.tram.commands.consumer.CommandHandlerReplyBuilder.withSuccess;

/** Local saga participant for CancelOrderSaga. */
@Component
public class CancelOrderSagaLocalSteps {

    private static final Logger logger = LoggerFactory.getLogger(CancelOrderSagaLocalSteps.class);

    private final OrderRepository orderRepository;
    private final DomainEventPublisher eventPublisher;
    private final Counter ordersCancelledCounter;
    private final Counter sagaFailuresCounter;

    public CancelOrderSagaLocalSteps(
        OrderRepository orderRepository,
        DomainEventPublisher eventPublisher,
        MeterRegistry meterRegistry
    ) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
        this.ordersCancelledCounter = Counter.builder("order_service_cancelled_orders_total")
            .description("Total number of cancelled orders")
            .register(meterRegistry);
        this.sagaFailuresCounter = Counter.builder("order_service_saga_failures_total")
            .description("Total number of saga failures")
            .tag("saga", "CancelOrderSaga")
            .register(meterRegistry);
    }

    public CommandHandlers commandHandlers() {
        return SagaCommandHandlersBuilder
            .fromChannel(ChannelNames.CANCEL_ORDER_SAGA_COMMAND_CHANNEL)
            .onMessage(BeginCancelCommand.class, this::beginCancel)
            .onMessage(UndoCancelCommand.class, this::undoCancel)
            .onMessage(ConfirmCancelCommand.class, this::confirmCancel)
            .onMessage(NoopFinancialSettlementCommand.class, this::noopFinancialSettlement)
            .build();
    }

    @Transactional
    public Message beginCancel(CommandMessage<BeginCancelCommand> cm) {
        beginCancelOrder(cm.getCommand().getOrderId());
        return withSuccess();
    }

    @Transactional
    public void beginCancelOrder(Long orderId) {
        logger.info("Beginning order cancellation: orderId={}", orderId);
        Order order = requireOrder(orderId);
        order.beginCancel();
        orderRepository.save(order);
    }

    @Transactional
    public Message undoCancel(CommandMessage<UndoCancelCommand> cm) {
        undoCancelOrder(cm.getCommand().getOrderId());
        return withSuccess();
    }

    @Transactional
    public void undoCancelOrder(Long orderId) {
        logger.warn("Undoing order cancellation due to saga failure: orderId={}", orderId);
        Order order = requireOrder(orderId);
        order.undoCancel();
        orderRepository.save(order);
        sagaFailuresCounter.increment();
    }

    @Transactional
    public void recordFinancialSettlement(
        Long orderId,
        OrderPaymentState originalPaymentState,
        Long authorizationId,
        Long captureId
    ) {
        Order order = requireOrder(orderId);
        switch (originalPaymentState) {
            case AUTHORIZED -> order.markPaymentVoided(
                "cancel-order-" + orderId + "-authorization-" + authorizationId + "-void"
            );
            case CAPTURED -> order.markPaymentRefunded(
                "cancel-order-" + orderId + "-capture-" + captureId + "-refund"
            );
            case VOIDED, REFUNDED -> {
                // Durable no-op: state already proves settlement completed.
            }
            default -> throw new IllegalStateException(
                "Cancellation cannot settle financial state " + originalPaymentState);
        }
        orderRepository.saveAndFlush(order);
    }

    @Transactional
    public Message noopFinancialSettlement(
        CommandMessage<NoopFinancialSettlementCommand> cm
    ) {
        try {
            NoopFinancialSettlementCommand command = cm.getCommand();
            if (command.getPaymentState() != OrderPaymentState.VOIDED
                && command.getPaymentState() != OrderPaymentState.REFUNDED) {
                return withFailure(
                    "Financial settlement no-op is invalid for " + command.getPaymentState());
            }
            return withSuccess();
        } catch (IllegalArgumentException | IllegalStateException e) {
            return withFailure(e.getMessage());
        }
    }

    @Transactional
    public Message confirmCancel(CommandMessage<ConfirmCancelCommand> cm) {
        Long orderId = cm.getCommand().getOrderId();
        Order order = requireOrder(orderId);
        order.confirmCancel();
        orderRepository.saveAndFlush(order);
        eventPublisher.publishOrderEvent(
            order.getId(),
            order.getVersion().longValue(),
            new OrderCancelled(
                order.getId(),
                order.getConsumerId(),
                order.getRestaurantId()
            )
        );
        ordersCancelledCounter.increment();
        logger.info("Order cancelled: orderId={}, state={}, version={}",
            orderId, order.getState(), order.getVersion());
        return withSuccess();
    }

    private Order requireOrder(Long orderId) {
        return orderRepository.findByIdWithLock(orderId)
            .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
    }

    public static class BeginCancelCommand implements Command {
        private Long orderId;
        public BeginCancelCommand() { }
        public BeginCancelCommand(Long orderId) { this.orderId = orderId; }
        public Long getOrderId() { return orderId; }
        public void setOrderId(Long orderId) { this.orderId = orderId; }
    }

    public static class UndoCancelCommand implements Command {
        private Long orderId;
        public UndoCancelCommand() { }
        public UndoCancelCommand(Long orderId) { this.orderId = orderId; }
        public Long getOrderId() { return orderId; }
        public void setOrderId(Long orderId) { this.orderId = orderId; }
    }

    public static class ConfirmCancelCommand implements Command {
        private Long orderId;
        public ConfirmCancelCommand() { }
        public ConfirmCancelCommand(Long orderId) { this.orderId = orderId; }
        public Long getOrderId() { return orderId; }
        public void setOrderId(Long orderId) { this.orderId = orderId; }
    }

    public static class NoopFinancialSettlementCommand implements Command {
        private Long orderId;
        private OrderPaymentState paymentState;

        public NoopFinancialSettlementCommand() { }

        public NoopFinancialSettlementCommand(Long orderId, OrderPaymentState paymentState) {
            this.orderId = orderId;
            this.paymentState = paymentState;
        }

        public Long getOrderId() { return orderId; }
        public void setOrderId(Long orderId) { this.orderId = orderId; }
        public OrderPaymentState getPaymentState() { return paymentState; }
        public void setPaymentState(OrderPaymentState paymentState) {
            this.paymentState = paymentState;
        }
    }
}
