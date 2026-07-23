package net.ftgo.order.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.eventuate.tram.sagas.orchestration.SagaInstanceFactory;
import net.ftgo.common.channels.ChannelNames;
import net.ftgo.common.orderflow.events.TicketAcceptanceTimedOutEvent;
import net.ftgo.common.orderflow.events.TicketAcceptedEvent;
import net.ftgo.common.orderflow.events.TicketRejectedEvent;
import net.ftgo.order.domain.Order;
import net.ftgo.order.repository.OrderRepository;
import net.ftgo.order.saga.ConfirmOrderSaga;
import net.ftgo.order.saga.ConfirmOrderSagaData;
import net.ftgo.order.saga.RejectOrderSaga;
import net.ftgo.order.saga.RejectOrderSagaData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;

/**
 * Converts kitchen acceptance decisions into order-level decision sagas.
 *
 * <p>The order row is locked before claiming the event. The first valid event
 * wins; duplicates, stale events and the losing side of an accept/timeout race
 * are acknowledged as no-ops.</p>
 */
@Component
@Profile("!test")
public class TicketDecisionEventHandler {

    private static final Logger logger = LoggerFactory.getLogger(TicketDecisionEventHandler.class);

    private final OrderRepository orderRepository;
    private final SagaInstanceFactory sagaInstanceFactory;
    private final ConfirmOrderSaga confirmOrderSaga;
    private final RejectOrderSaga rejectOrderSaga;
    private final ObjectMapper objectMapper;

    public TicketDecisionEventHandler(
        OrderRepository orderRepository,
        SagaInstanceFactory sagaInstanceFactory,
        ConfirmOrderSaga confirmOrderSaga,
        RejectOrderSaga rejectOrderSaga,
        ObjectMapper objectMapper
    ) {
        this.orderRepository = orderRepository;
        this.sagaInstanceFactory = sagaInstanceFactory;
        this.confirmOrderSaga = confirmOrderSaga;
        this.rejectOrderSaga = rejectOrderSaga;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
        topics = ChannelNames.TICKET_EVENT_TOPIC,
        groupId = "order-service-ticket-decisions",
        containerFactory = "ticketDecisionKafkaListenerContainerFactory"
    )
    @Transactional
    public void handleTicketEvent(
        @Payload String payload,
        @Header(KafkaHeaders.RECEIVED_KEY) String key,
        @Header(value = "eventType", required = false) String eventType
    ) {
        if (eventType == null) {
            logger.warn("Ignoring ticket event without eventType: key={}", key);
            return;
        }

        try {
            switch (eventType) {
                case "TicketAcceptedEvent" -> handleAccepted(
                    objectMapper.readValue(payload, TicketAcceptedEvent.class)
                );
                case "TicketRejectedEvent" -> handleRejected(
                    objectMapper.readValue(payload, TicketRejectedEvent.class)
                );
                case "TicketAcceptanceTimedOutEvent" -> handleTimedOut(
                    objectMapper.readValue(payload, TicketAcceptanceTimedOutEvent.class)
                );
                default -> logger.debug(
                    "Ignoring non-decision ticket event: key={}, eventType={}",
                    key,
                    eventType
                );
            }
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(
                "Unable to deserialize ticket decision event " + eventType,
                e
            );
        }
    }

    public void handleAccepted(TicketAcceptedEvent event) {
        Optional<Order> optionalOrder = matchingOrder(event.getOrderId(), event.getTicketId());
        if (optionalOrder.isEmpty()) {
            return;
        }

        Order order = optionalOrder.get();
        if (!order.claimRestaurantAcceptance()) {
            logger.info(
                "Ignoring duplicate or stale acceptance: eventId={}, orderId={}, state={}",
                event.getEventId(),
                event.getOrderId(),
                order.getState()
            );
            return;
        }

        orderRepository.save(order);
        sagaInstanceFactory.create(confirmOrderSaga, new ConfirmOrderSagaData(
            order.getId(),
            order.getConsumerId(),
            order.getAuthorizationId(),
            order.getCreditReservationId()
        ));
    }

    public void handleRejected(TicketRejectedEvent event) {
        claimRejection(
            event.getEventId(),
            event.getOrderId(),
            event.getTicketId(),
            event.getReason(),
            "Restaurant rejected the order: " + event.getReason()
        );
    }

    public void handleTimedOut(TicketAcceptanceTimedOutEvent event) {
        claimRejection(
            event.getEventId(),
            event.getOrderId(),
            event.getTicketId(),
            "ACCEPTANCE_TIMEOUT",
            "Restaurant acceptance deadline elapsed"
        );
    }

    private void claimRejection(
        String eventId,
        Long orderId,
        Long ticketId,
        String failureCode,
        String failureMessage
    ) {
        Optional<Order> optionalOrder = matchingOrder(orderId, ticketId);
        if (optionalOrder.isEmpty()) {
            return;
        }

        Order order = optionalOrder.get();
        if (!order.claimRestaurantRejection(failureCode, failureMessage)) {
            logger.info(
                "Ignoring duplicate or stale rejection: eventId={}, orderId={}, state={}",
                eventId,
                orderId,
                order.getState()
            );
            return;
        }

        orderRepository.save(order);
        sagaInstanceFactory.create(rejectOrderSaga, new RejectOrderSagaData(
            order.getId(),
            order.getConsumerId(),
            order.getAuthorizationId(),
            order.getCreditReservationId(),
            failureCode,
            failureMessage
        ));
    }

    private Optional<Order> matchingOrder(Long orderId, Long ticketId) {
        if (orderId == null || ticketId == null) {
            logger.warn(
                "Ignoring malformed ticket decision: orderId={}, ticketId={}",
                orderId,
                ticketId
            );
            return Optional.empty();
        }

        Optional<Order> optionalOrder = orderRepository.findByIdWithLock(orderId);
        if (optionalOrder.isEmpty()) {
            logger.warn("Ignoring ticket decision for unknown order: orderId={}", orderId);
            return Optional.empty();
        }

        Order order = optionalOrder.get();
        if (!Objects.equals(ticketId, order.getTicketId())) {
            logger.warn(
                "Ignoring ticket decision with mismatched ticket: orderId={}, expected={}, actual={}",
                orderId,
                order.getTicketId(),
                ticketId
            );
            return Optional.empty();
        }
        return optionalOrder;
    }
}
