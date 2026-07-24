package net.ftgo.common.messaging;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DomainEventEnvelopeTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Test
    void serializesStableVersionedEnvelopeContract() throws Exception {
        UUID eventId = UUID.fromString("8a65c9bc-2107-4f47-9ae1-43f3140ae223");
        Instant occurredAt = Instant.parse("2026-07-24T08:15:30.123456Z");
        TraceContext trace = new TraceContext(
            "4bf92f3577b34da6a3ce929d0e0e4736",
            "00f067aa0ba902b7",
            true
        );
        DomainEventEnvelope<Map<String, Object>> envelope = new DomainEventEnvelope<>(
            eventId,
            "OrderApproved",
            1,
            "Order",
            "101",
            7L,
            occurredAt,
            "correlation-123",
            "causation-456",
            trace,
            Map.of("orderId", 101L)
        );

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(envelope));

        assertEquals(eventId.toString(), json.path("eventId").asText());
        assertEquals("OrderApproved", json.path("eventType").asText());
        assertEquals(1, json.path("schemaVersion").asInt());
        assertEquals("Order", json.path("aggregateType").asText());
        assertEquals("101", json.path("aggregateId").asText());
        assertEquals(7L, json.path("aggregateVersion").asLong());
        assertEquals("2026-07-24T08:15:30.123456Z", json.path("occurredAt").asText());
        assertEquals("correlation-123", json.path("correlationId").asText());
        assertEquals("causation-456", json.path("causationId").asText());
        assertEquals("4bf92f3577b34da6a3ce929d0e0e4736", json.path("trace").path("traceId").asText());
        assertEquals("00f067aa0ba902b7", json.path("trace").path("spanId").asText());
        assertFalse(json.path("trace").path("sampled").isMissingNode());
        assertEquals(101L, json.path("payload").path("orderId").asLong());
    }

    @Test
    void deserializesEnvelopeWithUnknownAdditiveFields() throws Exception {
        String json = """
            {
              "eventId": "8a65c9bc-2107-4f47-9ae1-43f3140ae223",
              "eventType": "OrderApproved",
              "schemaVersion": 1,
              "aggregateType": "Order",
              "aggregateId": "101",
              "aggregateVersion": 7,
              "occurredAt": "2026-07-24T08:15:30.123456Z",
              "correlationId": "correlation-123",
              "causationId": "causation-456",
              "trace": {
                "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
                "spanId": "00f067aa0ba902b7",
                "sampled": true,
                "futureTraceField": "ignored"
              },
              "payload": {"orderId": 101},
              "futureEnvelopeField": "ignored"
            }
            """;

        DomainEventEnvelope<JsonNode> envelope = objectMapper.readValue(
            json,
            new TypeReference<DomainEventEnvelope<JsonNode>>() { }
        );

        assertEquals(UUID.fromString("8a65c9bc-2107-4f47-9ae1-43f3140ae223"), envelope.eventId());
        assertEquals(Instant.parse("2026-07-24T08:15:30.123456Z"), envelope.occurredAt());
        assertNotNull(envelope.trace());
        assertEquals("4bf92f3577b34da6a3ce929d0e0e4736", envelope.trace().traceId());
        assertEquals(101L, envelope.payload().path("orderId").asLong());
    }
}
