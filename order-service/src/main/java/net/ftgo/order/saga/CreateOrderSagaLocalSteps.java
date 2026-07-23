package net.ftgo.order.saga;

import io.eventuate.tram.commands.consumer.CommandHandlers;
import io.eventuate.tram.commands.consumer.CommandMessage;
import io.eventuate.tram.messaging.common.Message;
import io.eventuate.tram.sagas.participant.SagaCommandHandlersBuilder;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import net.ftgo.common.Address;
import net.ftgo.common.orderflow.events.OrderApproved;
import net.ftgo.common.orderflow.events.OrderRejected;
import net.ftgo.order.domain.Order;
import net.ftgo.order.domain.OrderState;
import net.ftgo.order.messaging.DomainEventPublisher;
import net.ftgo.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import static io.eventuate.tram.commands.consumer.CommandHandlerReplyBuilder.withSuccess;

/**
 * Local aggregate transitions used by CreateOrderSaga.
 *
 * <p>The class is registered explicitly by CreateOrderSagaConfiguration to
 * avoid duplicate component and configuration beans.</p>
 */
public class CreateOrderSagaLocalSteps {

    private static final Logger logger = LoggerFactory.getLogger(CreateOrderSagaLocalSteps.class);

    private final OrderRepository orderRepository;
    private final DomainEventPublisher eventPublisher;
    private final Counter ordersApprovedCounter;
    private final Counter ordersRejectedCounter;
    private final Counter sagaFailuresCounter;

    public CreateOrderSagaLocalSteps(
        OrderRepository orderRepository,
        DomainEventPublisher eventPublisher,
        MeterRegistry meterRegistry
    ) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
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

    public CommandHandlers commandHandlers() {
        return SagaCommandHandlersBuilder
            .fromChannel("orderService")
            .onMessage(RejectOrderCommand.class, this::rejectOrder)
            .onMessage(ApproveOrderCommand.class, this::approveOrder)
            .build();
    }

    /**
     * Final successful CreateOrderSaga transition. Approval is intentionally
     * deferred until a TicketAcceptedEvent starts ConfirmOrderSaga.
     */
    @Transactional
    public boolean awaitRestaurantAcceptance(CreateOrderSagaData data) {
        Order order = orderRepository.findByIdWithLock(data.getOrderId())
            .orElseThrow(() -> new IllegalArgumentException(
                "Order not found: " + data.getOrderId()
            ));

        boolean changed = order.awaitRestaurantAcceptance(
            data.getTicketId(),
            data.getAuthorizationId(),
            data.getCreditReservationId(),
            data.getAcceptanceDeadline()
        );
        if (changed) {
            orderRepository.save(order);
        }
        return changed;
    }

    /**
     * CreateOrderSaga compensation. Duplicate compensation is a successful
     * no-op and does not publish another rejection event.
     */
    @Transactional
    public Message rejectOrder(CommandMessage<RejectOrderCommand> message) {
        Long orderId = message.getCommand().getOrderId();
        Order order = orderRepository.findByIdWithLock(orderId)
            .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        if (order.getState() == OrderState.REJECTED) {
            return withSuccess();
        }
        if (order.getState() != OrderState.APPROVAL_PENDING) {
            throw new IllegalStateException(
                "Cannot compensate create order in state " + order.getState()
            );
        }

        order.reject();
        orderRepository.save(order);
        eventPublisher.publishOrderEvent(order.getId(), new OrderRejected(
            order.getId(),
            order.getConsumerId(),
            order.getRestaurantId(),
            "CREATE_ORDER_COMPENSATION:Order preparation failed"
        ));
        ordersRejectedCounter.increment();
        sagaFailuresCounter.increment();
        logger.warn("CreateOrderSaga compensated order {}", orderId);
        return withSuccess();
    }

    /**
     * Legacy Phase 01 endpoint retained during rolling deployment. The new
     * CreateOrderSaga never invokes this command.
     */
    @Transactional
    public Message approveOrder(CommandMessage<ApproveOrderCommand> message) {
        ApproveOrderCommand command = message.getCommand();
        Order order = orderRepository.findByIdWithLock(command.getOrderId())
            .orElseThrow(() -> new IllegalArgumentException(
                "Order not found: " + command.getOrderId()
            ));

        if (order.getState() == OrderState.APPROVED) {
            return withSuccess();
        }

        order.setTicketId(command.getTicketId());
        order.setAuthorizationId(command.getAuthorizationId());
        order.approve();
        orderRepository.save(order);
        eventPublisher.publishOrderEvent(order.getId(), new OrderApproved(
            order.getId(),
            order.getConsumerId(),
            order.getRestaurantId(),
            order.getOrderTotal(),
            command.getTicketId(),
            command.getAuthorizationId(),
            toAddress(order.getDeliveryInfo().getDeliveryAddress()),
            order.getDeliveryInfo().getDeliveryTime()
        ));
        ordersApprovedCounter.increment();
        return withSuccess();
    }

    private Address toAddress(String deliveryAddress) {
        String[] streetCityStateZip = deliveryAddress.split(", ", 3);
        if (streetCityStateZip.length != 3) {
            return new Address(deliveryAddress, "Unknown", "NA", "00000");
        }

        String[] stateZip = streetCityStateZip[2].split(" ", 2);
        if (stateZip.length != 2) {
            return new Address(deliveryAddress, "Unknown", "NA", "00000");
        }
        return new Address(
            streetCityStateZip[0],
            streetCityStateZip[1],
            stateZip[0],
            stateZip[1]
        );
    }

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

    public static class ApproveOrderCommand implements io.eventuate.tram.commands.common.Command {
        private Long orderId;
        private Long ticketId;
        private Long authorizationId;

        public ApproveOrderCommand() {
        }

        public ApproveOrderCommand(Long orderId, Long ticketId, Long authorizationId) {
            this.orderId = orderId;
            this.ticketId = ticketId;
            this.authorizationId = authorizationId;
        }

        public Long getOrderId() {
            return orderId;
        }

        public void setOrderId(Long orderId) {
            this.orderId = orderId;
        }

        public Long getTicketId() {
            return ticketId;
        }

        public void setTicketId(Long ticketId) {
            this.ticketId = ticketId;
        }

        public Long getAuthorizationId() {
            return authorizationId;
        }

        public void setAuthorizationId(Long authorizationId) {
            this.authorizationId = authorizationId;
        }
    }
}
