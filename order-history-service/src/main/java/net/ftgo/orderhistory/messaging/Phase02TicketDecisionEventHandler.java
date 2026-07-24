package net.ftgo.orderhistory.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.ftgo.common.messaging.OutboxEventPayloadReader;
import net.ftgo.common.orderflow.events.TicketAcceptanceTimedOutEvent;
import net.ftgo.common.orderflow.events.TicketRejectedEvent;
import net.ftgo.orderhistory.domain.OrderHistoryRecord;
import net.ftgo.orderhistory.domain.ProcessedMessage;
import net.ftgo.orderhistory.repository.OrderHistoryRepository;
import net.ftgo.orderhistory.repository.ProcessedMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Phase 02 projection adapter for terminal restaurant ticket decisions.
 *
 * <p>The legacy order-history ticket listener remains responsible for
 * TicketAcceptedEvent and TicketReadyEvent during rolling deployment. This
 * listener uses a dedicated consumer group so rejected and timed-out decisions
 * cannot be lost when an older instance ignores their event types.</p>
 */
@Component
public class Phase02TicketDecisionEventHandler {

    private static final Logger logger =
        LoggerFactory.getLogger(Phase02TicketDecisionEventHandler.class);

    private final OrderHistoryRepository orderHistoryRepository;
    private final ProcessedMessageRepository processedMessageRepository;
    private final ObjectMapper objectMapper;

    public Phase02TicketDecisionEventHandler(
        OrderHistoryRepository orderHistoryRepository,
        ProcessedMessageRepository processedMessageRepository,
        ObjectMapper objectMapper
    ) {
        this.orderHistoryRepository = orderHistoryRepository;
        this.processedMessageRepository = processedMessageRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
        topics = "net.ftgo.kitchenservice.domain.Ticket",
        groupId = "order-history-service-phase02-ticket-decisions",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void handleTicketDecision(
        @Payload String payload,
        @Header(KafkaHeaders.RECEIVED_KEY) String key,
        @Header(value = "eventType", required = false) String eventType
    ) {
        if (!"TicketRejectedEvent".equals(eventType)
            && !"TicketAcceptanceTimedOutEvent".equals(eventType)) {
            return;
        }

        String messageId = key + "-" + eventType;
        if (processedMessageRepository.existsById(messageId)) {
            logger.info("Skipping duplicate Phase 02 ticket decision: {}", messageId);
            return;
        }

        try {
            if ("TicketRejectedEvent".equals(eventType)) {
                TicketRejectedEvent event = OutboxEventPayloadReader.read(
                    objectMapper,
                    payload,
                    TicketRejectedEvent.class
                );
                updateTicketStatus(event.getOrderId(), "REJECTED_BY_RESTAURANT");
            } else {
                TicketAcceptanceTimedOutEvent event = OutboxEventPayloadReader.read(
                    objectMapper,
                    payload,
                    TicketAcceptanceTimedOutEvent.class
                );
                updateTicketStatus(event.getOrderId(), "REJECTED_TIMEOUT");
            }
            processedMessageRepository.save(new ProcessedMessage(messageId));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(
                "Failed to deserialize Phase 02 ticket decision " + eventType,
                e
            );
        }
    }

    private void updateTicketStatus(Long orderId, String ticketStatus) {
        Optional<OrderHistoryRecord> record =
            orderHistoryRepository.findById(orderId.toString());
        if (record.isEmpty()) {
            logger.warn(
                "Order history record not found for ticket decision: orderId={}, status={}",
                orderId,
                ticketStatus
            );
            return;
        }
        record.get().setTicketStatus(ticketStatus);
        orderHistoryRepository.save(record.get());
    }
}
