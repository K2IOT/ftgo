package net.ftgo.common.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Objects;

/**
 * Reads an outbox event from legacy direct JSON, a Kafka Connect JSON wrapper,
 * or the Phase 03 versioned domain-event envelope.
 */
public final class OutboxEventPayloadReader {

    private static final int MAX_WRAPPER_DEPTH = 4;

    private OutboxEventPayloadReader() {
    }

    public static <T> T read(
        ObjectMapper objectMapper,
        String message,
        Class<T> eventType
    ) throws JsonProcessingException {
        Objects.requireNonNull(objectMapper, "objectMapper");
        Objects.requireNonNull(eventType, "eventType");

        JsonNode eventNode = objectMapper.readTree(message);
        for (int depth = 0; depth < MAX_WRAPPER_DEPTH; depth++) {
            if (eventNode != null && eventNode.isTextual()) {
                eventNode = objectMapper.readTree(eventNode.textValue());
                continue;
            }
            if (isKafkaConnectEnvelope(eventNode) || isDomainEventEnvelope(eventNode)) {
                eventNode = eventNode.get("payload");
                continue;
            }
            break;
        }

        if (eventNode == null || eventNode.isNull() || !eventNode.isObject()) {
            throw new IllegalArgumentException("Outbox event payload is missing or invalid");
        }

        return objectMapper.treeToValue(eventNode, eventType);
    }

    private static boolean isKafkaConnectEnvelope(JsonNode node) {
        return node != null
            && node.isObject()
            && node.has("schema")
            && node.has("payload");
    }

    private static boolean isDomainEventEnvelope(JsonNode node) {
        return node != null
            && node.isObject()
            && node.hasNonNull("eventId")
            && node.hasNonNull("eventType")
            && node.hasNonNull("schemaVersion")
            && node.hasNonNull("aggregateType")
            && node.hasNonNull("aggregateId")
            && node.has("payload");
    }
}
