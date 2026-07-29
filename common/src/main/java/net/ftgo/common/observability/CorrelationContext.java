package net.ftgo.common.observability;

import net.ftgo.common.messaging.DomainEventMetadata;
import net.ftgo.common.messaging.TraceContext;
import org.slf4j.MDC;

import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Request and message correlation context shared by HTTP, saga, outbox and
 * Kafka boundaries.
 *
 * <p>The OpenTelemetry agent owns span creation. This class owns the stable
 * application correlation ID, W3C carrier values and MDC lifecycle. Scopes
 * always restore the previous thread state to avoid leaking identifiers
 * between pooled request or consumer threads.</p>
 */
public final class CorrelationContext {

    public static final String TRACEPARENT = "traceparent";
    public static final String TRACESTATE = "tracestate";
    public static final String BAGGAGE = "baggage";
    public static final String CORRELATION_ID = "X-Correlation-ID";
    public static final String CAUSATION_ID = "X-Causation-ID";
    public static final String CORRELATION_BAGGAGE_KEY = "ftgo.correlation_id";

    private static final Pattern TRACEPARENT_PATTERN = Pattern.compile(
        "^(?!ff)([0-9a-f]{2})-((?!0{32})[0-9a-f]{32})-((?!0{16})[0-9a-f]{16})-([0-9a-f]{2})$"
    );
    private static final Pattern SAFE_ID = Pattern.compile("^[A-Za-z0-9._-]{1,128}$");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final ThreadLocal<Snapshot> CURRENT = new ThreadLocal<>();

    private CorrelationContext() {
    }

    public static Snapshot resolve(Map<String, String> carrier) {
        Map<String, String> safeCarrier = carrier == null ? Map.of() : carrier;
        String traceparent = validTraceparent(value(safeCarrier, TRACEPARENT))
            .orElseGet(CorrelationContext::newTraceparent);
        String correlationId = validId(value(safeCarrier, CORRELATION_ID))
            .orElseGet(() -> traceParts(traceparent).traceId());
        String causationId = validId(value(safeCarrier, CAUSATION_ID)).orElse(null);
        String tracestate = trimToNull(value(safeCarrier, TRACESTATE));
        String baggage = withCorrelationBaggage(
            trimToNull(value(safeCarrier, BAGGAGE)),
            correlationId
        );
        return new Snapshot(correlationId, causationId, traceparent, tracestate, baggage);
    }

    public static Snapshot fromMetadata(DomainEventMetadata metadata) {
        if (metadata == null) {
            return resolve(Map.of());
        }
        TraceContext trace = metadata.trace();
        Map<String, String> carrier = new LinkedHashMap<>();
        putIfPresent(carrier, CORRELATION_ID, metadata.correlationId());
        putIfPresent(carrier, CAUSATION_ID, metadata.causationId());
        if (trace != null) {
            String traceparent = trimToNull(trace.traceparent());
            if (traceparent == null && validTraceId(trace.traceId()) && validSpanId(trace.spanId())) {
                traceparent = "00-" + trace.traceId().toLowerCase() + "-"
                    + trace.spanId().toLowerCase() + "-" + (trace.sampled() ? "01" : "00");
            }
            putIfPresent(carrier, TRACEPARENT, traceparent);
            putIfPresent(carrier, TRACESTATE, trace.tracestate());
            putIfPresent(carrier, BAGGAGE, trace.baggage());
        }
        return resolve(carrier);
    }

    public static Optional<Snapshot> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static DomainEventMetadata currentMetadata() {
        Snapshot snapshot = CURRENT.get();
        if (snapshot == null) {
            return DomainEventMetadata.empty();
        }
        TraceParts parts = traceParts(snapshot.traceparent());
        return new DomainEventMetadata(
            snapshot.correlationId(),
            snapshot.causationId(),
            new TraceContext(
                parts.traceId(),
                parts.spanId(),
                parts.sampled(),
                snapshot.traceparent(),
                snapshot.tracestate(),
                snapshot.baggage()
            )
        );
    }

    public static DomainEventMetadata enrich(DomainEventMetadata metadata) {
        DomainEventMetadata current = currentMetadata();
        if (metadata == null || metadata == DomainEventMetadata.empty()) {
            return current;
        }
        return new DomainEventMetadata(
            firstNonBlank(metadata.correlationId(), current.correlationId()),
            firstNonBlank(metadata.causationId(), current.causationId()),
            metadata.trace() == null ? current.trace() : metadata.trace()
        );
    }

    public static Scope open(Snapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot is required");
        Snapshot previous = CURRENT.get();
        Map<String, String> previousMdc = MDC.getCopyOfContextMap();
        CURRENT.set(snapshot);
        TraceParts parts = traceParts(snapshot.traceparent());
        MDC.put("correlationId", snapshot.correlationId());
        MDC.put("traceId", parts.traceId());
        MDC.put("spanId", parts.spanId());
        if (snapshot.causationId() == null) {
            MDC.remove("causationId");
        } else {
            MDC.put("causationId", snapshot.causationId());
        }
        return new Scope(previous, previousMdc);
    }

    public static void inject(Snapshot snapshot, BiConsumer<String, String> setter) {
        Objects.requireNonNull(snapshot, "snapshot is required");
        Objects.requireNonNull(setter, "setter is required");
        setter.accept(TRACEPARENT, snapshot.traceparent());
        setIfPresent(setter, TRACESTATE, snapshot.tracestate());
        setIfPresent(setter, BAGGAGE, snapshot.baggage());
        setter.accept(CORRELATION_ID, snapshot.correlationId());
        setIfPresent(setter, CAUSATION_ID, snapshot.causationId());
    }

    private static Optional<String> validTraceparent(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return Optional.empty();
        }
        normalized = normalized.toLowerCase();
        return TRACEPARENT_PATTERN.matcher(normalized).matches()
            ? Optional.of(normalized)
            : Optional.empty();
    }

    private static Optional<String> validId(String value) {
        String normalized = trimToNull(value);
        return normalized != null && SAFE_ID.matcher(normalized).matches()
            ? Optional.of(normalized)
            : Optional.empty();
    }

    private static boolean validTraceId(String value) {
        return value != null && value.matches("(?i)(?!0{32})[0-9a-f]{32}");
    }

    private static boolean validSpanId(String value) {
        return value != null && value.matches("(?i)(?!0{16})[0-9a-f]{16}");
    }

    private static String newTraceparent() {
        return "00-" + randomHex(16) + "-" + randomHex(8) + "-01";
    }

    private static String randomHex(int bytesLength) {
        byte[] bytes = new byte[bytesLength];
        do {
            RANDOM.nextBytes(bytes);
        } while (allZero(bytes));
        StringBuilder builder = new StringBuilder(bytesLength * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value & 0xff));
        }
        return builder.toString();
    }

    private static boolean allZero(byte[] bytes) {
        for (byte value : bytes) {
            if (value != 0) {
                return false;
            }
        }
        return true;
    }

    private static TraceParts traceParts(String traceparent) {
        Matcher matcher = TRACEPARENT_PATTERN.matcher(traceparent);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid canonical traceparent");
        }
        int flags = Integer.parseInt(matcher.group(4), 16);
        return new TraceParts(matcher.group(2), matcher.group(3), (flags & 1) == 1);
    }

    private static String withCorrelationBaggage(String baggage, String correlationId) {
        String entry = CORRELATION_BAGGAGE_KEY + "=" + correlationId;
        if (baggage == null) {
            return entry;
        }
        String[] members = baggage.split(",");
        StringBuilder result = new StringBuilder();
        for (String member : members) {
            String trimmed = member.trim();
            if (trimmed.isEmpty() || trimmed.startsWith(CORRELATION_BAGGAGE_KEY + "=")) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append(',');
            }
            result.append(trimmed);
        }
        if (!result.isEmpty()) {
            result.append(',');
        }
        return result.append(entry).toString();
    }

    private static String value(Map<String, String> carrier, String name) {
        String direct = carrier.get(name);
        if (direct != null) {
            return direct;
        }
        for (Map.Entry<String, String> entry : carrier.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String firstNonBlank(String preferred, String fallback) {
        String value = trimToNull(preferred);
        return value == null ? fallback : value;
    }

    private static void putIfPresent(Map<String, String> target, String key, String value) {
        String normalized = trimToNull(value);
        if (normalized != null) {
            target.put(key, normalized);
        }
    }

    private static void setIfPresent(
        BiConsumer<String, String> setter,
        String key,
        String value
    ) {
        if (value != null) {
            setter.accept(key, value);
        }
    }

    public record Snapshot(
        String correlationId,
        String causationId,
        String traceparent,
        String tracestate,
        String baggage
    ) {
        public Snapshot {
            Objects.requireNonNull(correlationId, "correlationId is required");
            Objects.requireNonNull(traceparent, "traceparent is required");
        }
    }

    public static final class Scope implements AutoCloseable {
        private final Snapshot previous;
        private final Map<String, String> previousMdc;
        private boolean closed;

        private Scope(Snapshot previous, Map<String, String> previousMdc) {
            this.previous = previous;
            this.previousMdc = previousMdc;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
            MDC.clear();
            if (previousMdc != null) {
                MDC.setContextMap(previousMdc);
            }
        }
    }

    private record TraceParts(String traceId, String spanId, boolean sampled) {
    }
}
