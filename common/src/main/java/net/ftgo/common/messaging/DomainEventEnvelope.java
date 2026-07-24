package net.ftgo.common.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Stable transport envelope for versioned domain events.
 *
 * @param <T> business-event payload type
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DomainEventEnvelope<T>(
    UUID eventId,
    String eventType,
    int schemaVersion,
    String aggregateType,
    String aggregateId,
    long aggregateVersion,
    Instant occurredAt,
    String correlationId,
    String causationId,
    TraceContext trace,
    T payload
) {
    public DomainEventEnvelope {
        Objects.requireNonNull(eventId, "eventId");
        requireText(eventType, "eventType");
        if (schemaVersion < 1) {
            throw new IllegalArgumentException("schemaVersion must be positive");
        }
        requireText(aggregateType, "aggregateType");
        requireText(aggregateId, "aggregateId");
        if (aggregateVersion < 0) {
            throw new IllegalArgumentException("aggregateVersion cannot be negative");
        }
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(payload, "payload");
    }

    public static <T> DomainEventEnvelope<T> create(
        String eventType,
        int schemaVersion,
        String aggregateType,
        String aggregateId,
        long aggregateVersion,
        DomainEventMetadata metadata,
        T payload
    ) {
        DomainEventMetadata safeMetadata = metadata == null
            ? DomainEventMetadata.empty()
            : metadata;
        return new DomainEventEnvelope<>(
            UUID.randomUUID(),
            eventType,
            schemaVersion,
            aggregateType,
            aggregateId,
            aggregateVersion,
            Instant.now(),
            safeMetadata.correlationId(),
            safeMetadata.causationId(),
            safeMetadata.trace(),
            payload
        );
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be null or blank");
        }
    }
}
