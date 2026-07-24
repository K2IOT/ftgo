package net.ftgo.orderhistory.messaging;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.ftgo.common.orderflow.events.TicketAcceptanceTimedOutEvent;
import net.ftgo.common.orderflow.events.TicketRejectedEvent;
import net.ftgo.orderhistory.domain.OrderHistoryRecord;
import net.ftgo.orderhistory.repository.OrderHistoryRepository;
import net.ftgo.orderhistory.repository.ProcessedMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class Phase02TicketDecisionProjectionTest {

    @Mock
    private OrderHistoryRepository orderHistoryRepository;

    @Mock
    private ProcessedMessageRepository processedMessageRepository;

    private ObjectMapper objectMapper;
    private Phase02TicketDecisionEventHandler handler;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        handler = new Phase02TicketDecisionEventHandler(
            orderHistoryRepository,
            processedMessageRepository,
            objectMapper
        );
        when(processedMessageRepository.existsById(anyString())).thenReturn(false);
    }

    @Test
    void projectsExplicitRestaurantRejection() throws Exception {
        OrderHistoryRecord existing = new OrderHistoryRecord("101");
        when(orderHistoryRepository.findById("101")).thenReturn(Optional.of(existing));
        TicketRejectedEvent event = new TicketRejectedEvent(
            "decision-reject",
            202L,
            101L,
            "RESTAURANT_CAPACITY",
            LocalDateTime.now()
        );

        handler.handleTicketDecision(
            objectMapper.writeValueAsString(event),
            "Ticket#202",
            "TicketRejectedEvent"
        );

        ArgumentCaptor<OrderHistoryRecord> saved =
            ArgumentCaptor.forClass(OrderHistoryRecord.class);
        verify(orderHistoryRepository).save(saved.capture());
        assertThat(saved.getValue().getTicketStatus())
            .isEqualTo("REJECTED_BY_RESTAURANT");
        verify(processedMessageRepository).save(any());
    }

    @Test
    void projectsSchemaWrappedRestaurantRejection() throws Exception {
        OrderHistoryRecord existing = new OrderHistoryRecord("101");
        when(orderHistoryRepository.findById("101")).thenReturn(Optional.of(existing));
        TicketRejectedEvent event = new TicketRejectedEvent(
            "decision-reject",
            202L,
            101L,
            "RESTAURANT_CAPACITY",
            LocalDateTime.now()
        );
        String payload = objectMapper.writeValueAsString(Map.of(
            "schema", Map.of("type", "struct"),
            "payload", event
        ));

        handler.handleTicketDecision(
            payload,
            "Ticket#202",
            "TicketRejectedEvent"
        );

        ArgumentCaptor<OrderHistoryRecord> saved =
            ArgumentCaptor.forClass(OrderHistoryRecord.class);
        verify(orderHistoryRepository).save(saved.capture());
        assertThat(saved.getValue().getTicketStatus())
            .isEqualTo("REJECTED_BY_RESTAURANT");
        verify(processedMessageRepository).save(any());
    }

    @Test
    void projectsAcceptanceTimeout() throws Exception {
        OrderHistoryRecord existing = new OrderHistoryRecord("101");
        when(orderHistoryRepository.findById("101")).thenReturn(Optional.of(existing));
        TicketAcceptanceTimedOutEvent event = new TicketAcceptanceTimedOutEvent(
            "decision-timeout",
            202L,
            101L,
            LocalDateTime.now().minusSeconds(1),
            LocalDateTime.now()
        );

        handler.handleTicketDecision(
            objectMapper.writeValueAsString(event),
            "Ticket#202",
            "TicketAcceptanceTimedOutEvent"
        );

        ArgumentCaptor<OrderHistoryRecord> saved =
            ArgumentCaptor.forClass(OrderHistoryRecord.class);
        verify(orderHistoryRepository).save(saved.capture());
        assertThat(saved.getValue().getTicketStatus()).isEqualTo("REJECTED_TIMEOUT");
        verify(processedMessageRepository).save(any());
    }
}
