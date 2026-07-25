package net.ftgo.common.messaging;

import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/** Canonical Kafka headers used by the versioned domain-event contract. */
public final class KafkaEventHeaders {

    public static final String EVENT_ID = "id";
    public static final String EVENT_TYPE = "eventType";
    public static final String CORRELATION_ID = "correlationId";

    private KafkaEventHeaders() {
    }

    public static Optional<String> lastText(Headers headers, String name) {
        if (headers == null || name == null || name.isBlank()) {
            return Optional.empty();
        }
        Header header = headers.lastHeader(name);
        if (header == null || header.value() == null) {
            return Optional.empty();
        }
        String value = new String(header.value(), StandardCharsets.UTF_8).trim();
        return value.isEmpty() ? Optional.empty() : Optional.of(value);
    }

    public static String requiredText(Headers headers, String name) {
        return lastText(headers, name)
            .orElseThrow(() -> new IllegalArgumentException(
                "Missing required Kafka header: " + name
            ));
    }
}
