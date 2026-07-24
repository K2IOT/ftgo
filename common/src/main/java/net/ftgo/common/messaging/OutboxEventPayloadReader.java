package net.ftgo.common.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Objects;

/**
 * Reads an outbox event from either a direct JSON value or the schema envelope
 * emitted by Kafka Connect's JSON converter.
 */
public final class OutboxEventPayloadReader {

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
        if (eventNode != null && eventNode.isObject() && eventNode.has("payload")) {
            eventNode = eventNode.get("payload");
        }
        if (eventNode != null && eventNode.isTextual()) {
            eventNode = objectMapper.readTree(eventNode.textValue());
        }
        if (eventNode == null || eventNode.isNull() || !eventNode.isObject()) {
            throw new IllegalArgumentException("Outbox event payload is missing or invalid");
        }

        return objectMapper.treeToValue(eventNode, eventType);
    }
}
