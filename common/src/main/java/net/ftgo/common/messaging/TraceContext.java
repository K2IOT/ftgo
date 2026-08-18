package net.ftgo.common.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * W3C-compatible trace identifiers carried with a domain event.
 *
 * <p>The three-argument constructor preserves the version 1 JSON and Java
 * contract while the additional fields carry the exact W3C propagation
 * headers required to resume a trace after an outbox relay.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TraceContext(
    String traceId,
    String spanId,
    boolean sampled,
    String traceparent,
    String tracestate,
    String baggage
) {
    public TraceContext(String traceId, String spanId, boolean sampled) {
        this(traceId, spanId, sampled, null, null, null);
    }
}
