package net.ftgo.common.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Optional request and trace metadata propagated to a domain event.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DomainEventMetadata(
    String correlationId,
    String causationId,
    TraceContext trace
) {
    private static final DomainEventMetadata EMPTY = new DomainEventMetadata(null, null, null);

    public static DomainEventMetadata empty() {
        return EMPTY;
    }
}
