package net.ftgo.common.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.ftgo.common.orderflow.events.TicketAcceptedEvent;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxEventPayloadReaderTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void readsDirectEventJson() throws Exception {
        TicketAcceptedEvent expected = event();

        TicketAcceptedEvent actual = OutboxEventPayloadReader.read(
            objectMapper,
            objectMapper.writeValueAsString(expected),
            TicketAcceptedEvent.class
        );

        assertEvent(actual);
    }

    @Test
    void readsKafkaConnectSchemaEnvelope() throws Exception {
        TicketAcceptedEvent expected = event();
        String message = objectMapper.writeValueAsString(Map.of(
            "schema", Map.of("type", "struct"),
            "payload", expected
        ));

        TicketAcceptedEvent actual = OutboxEventPayloadReader.read(
            objectMapper,
            message,
            TicketAcceptedEvent.class
        );

        assertEvent(actual);
    }

    @Test
    void readsVersionedDomainEventEnvelope() throws Exception {
        DomainEventEnvelope<TicketAcceptedEvent> envelope = envelope();

        TicketAcceptedEvent actual = OutboxEventPayloadReader.read(
            objectMapper,
            objectMapper.writeValueAsString(envelope),
            TicketAcceptedEvent.class
        );

        assertEvent(actual);
    }

    @Test
    void readsKafkaConnectEnvelopeContainingVersionedDomainEvent() throws Exception {
        String message = objectMapper.writeValueAsString(Map.of(
            "schema", Map.of("type", "struct"),
            "payload", envelope()
        ));

        TicketAcceptedEvent actual = OutboxEventPayloadReader.read(
            objectMapper,
            message,
            TicketAcceptedEvent.class
        );

        assertEvent(actual);
    }

    @Test
    void readsTextualJsonPayload() throws Exception {
        String nestedJson = objectMapper.writeValueAsString(event());
        String message = objectMapper.writeValueAsString(Map.of(
            "schema", Map.of("type", "string"),
            "payload", nestedJson
        ));

        TicketAcceptedEvent actual = OutboxEventPayloadReader.read(
            objectMapper,
            message,
            TicketAcceptedEvent.class
        );

        assertEvent(actual);
    }

    private DomainEventEnvelope<TicketAcceptedEvent> envelope() {
        return DomainEventEnvelope.create(
            "TicketAcceptedEvent",
            1,
            "Ticket",
            "901",
            4L,
            new DomainEventMetadata("correlation-101", "command-202", null),
            event()
        );
    }

    private TicketAcceptedEvent event() {
        return new TicketAcceptedEvent(
            "decision-901",
            901L,
            101L,
            LocalDateTime.of(2026, 7, 24, 10, 30)
        );
    }

    private void assertEvent(TicketAcceptedEvent event) {
        assertThat(event.getEventId()).isEqualTo("decision-901");
        assertThat(event.getTicketId()).isEqualTo(901L);
        assertThat(event.getOrderId()).isEqualTo(101L);
        assertThat(event.getOccurredAt()).isEqualTo(LocalDateTime.of(2026, 7, 24, 10, 30));
    }
}
