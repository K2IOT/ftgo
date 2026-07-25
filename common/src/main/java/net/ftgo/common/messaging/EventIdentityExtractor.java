package net.ftgo.common.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.header.Headers;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/** Resolves the event identity independently from the aggregate partition key. */
public final class EventIdentityExtractor {

    private static final int MAX_WRAPPER_DEPTH = 4;

    private EventIdentityExtractor() {
    }

    public static UUID eventId(
        Headers headers,
        String message,
        ObjectMapper objectMapper
    ) {
        String headerValue = KafkaEventHeaders.requiredText(
            headers,
            KafkaEventHeaders.EVENT_ID
        );
        return eventId(headerValue, message, objectMapper);
    }

    public static UUID eventId(
        String headerValue,
        String message,
        ObjectMapper objectMapper
    ) {
        Objects.requireNonNull(objectMapper, "objectMapper");
        UUID headerEventId = parseUuid(headerValue, "Kafka event ID header");
        UUID envelopeEventId = envelopeEventId(message, objectMapper);
        if (envelopeEventId != null && !headerEventId.equals(envelopeEventId)) {
            throw new IllegalArgumentException(
                "Kafka event ID header does not match envelope eventId: header="
                    + headerEventId + ", envelope=" + envelopeEventId
            );
        }
        return headerEventId;
    }

    /** Deterministic compatibility identity for direct tests and legacy callers without headers. */
    public static UUID legacyEventId(String key, String eventType, String message) {
        String material = String.valueOf(key)
            + "\u0000" + String.valueOf(eventType)
            + "\u0000" + String.valueOf(message);
        return UUID.nameUUIDFromBytes(material.getBytes(StandardCharsets.UTF_8));
    }

    private static UUID envelopeEventId(String message, ObjectMapper objectMapper) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Event payload cannot be null or blank");
        }
        try {
            JsonNode node = objectMapper.readTree(message);
            for (int depth = 0; depth < MAX_WRAPPER_DEPTH; depth++) {
                if (node != null && node.isTextual()) {
                    node = objectMapper.readTree(node.textValue());
                    continue;
                }
                if (isKafkaConnectEnvelope(node)) {
                    node = node.get("payload");
                    continue;
                }
                break;
            }
            if (node != null && node.isObject() && node.hasNonNull("eventId")) {
                return parseUuid(node.get("eventId").asText(), "Envelope eventId");
            }
            return null;
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid event payload JSON", e);
        }
    }

    private static boolean isKafkaConnectEnvelope(JsonNode node) {
        return node != null
            && node.isObject()
            && node.has("schema")
            && node.has("payload");
    }

    private static UUID parseUuid(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be null or blank");
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(field + " is not a valid UUID: " + value, e);
        }
    }
}
