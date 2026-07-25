package net.ftgo.common.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * W3C-compatible trace identifiers carried with a domain event.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TraceContext(
    String traceId,
    String spanId,
    boolean sampled
) {
}
