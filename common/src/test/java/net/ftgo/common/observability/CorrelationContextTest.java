package net.ftgo.common.observability;

import net.ftgo.common.messaging.DomainEventMetadata;
import net.ftgo.common.messaging.TraceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationContextTest {

    private static final String TRACEPARENT =
        "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void resolvesAndInjectsCanonicalW3cHeaders() {
        CorrelationContext.Snapshot snapshot = CorrelationContext.resolve(Map.of(
            CorrelationContext.TRACEPARENT, TRACEPARENT,
            CorrelationContext.TRACESTATE, "vendor=value",
            CorrelationContext.BAGGAGE, "tenant=t-101",
            CorrelationContext.CORRELATION_ID, "corr-101"
        ));

        assertThat(snapshot.traceparent()).isEqualTo(TRACEPARENT);
        assertThat(snapshot.tracestate()).isEqualTo("vendor=value");
        assertThat(snapshot.correlationId()).isEqualTo("corr-101");
        assertThat(snapshot.baggage()).contains("tenant=t-101");
        assertThat(snapshot.baggage()).contains("ftgo.correlation_id=corr-101");

        Map<String, String> carrier = new LinkedHashMap<>();
        CorrelationContext.inject(snapshot, carrier::put);

        assertThat(carrier).containsEntry(CorrelationContext.TRACEPARENT, TRACEPARENT);
        assertThat(carrier).containsEntry(CorrelationContext.TRACESTATE, "vendor=value");
        assertThat(carrier).containsEntry(CorrelationContext.CORRELATION_ID, "corr-101");
        assertThat(carrier.get(CorrelationContext.BAGGAGE))
            .contains("ftgo.correlation_id=corr-101");
    }

    @Test
    void generatesValidIdentifiersWhenRequestHasNoContext() {
        CorrelationContext.Snapshot snapshot = CorrelationContext.resolve(Map.of());

        assertThat(snapshot.correlationId()).matches("[0-9a-f]{32}");
        assertThat(snapshot.traceparent())
            .matches("00-[0-9a-f]{32}-[0-9a-f]{16}-0[01]");
        assertThat(snapshot.baggage())
            .contains("ftgo.correlation_id=" + snapshot.correlationId());
    }

    @Test
    void opensMdcScopeAndBuildsDomainEventMetadata() {
        CorrelationContext.Snapshot snapshot = CorrelationContext.resolve(Map.of(
            CorrelationContext.TRACEPARENT, TRACEPARENT,
            CorrelationContext.CORRELATION_ID, "corr-202",
            CorrelationContext.CAUSATION_ID, "command-77"
        ));

        MDC.put("correlationId", "outer");
        try (CorrelationContext.Scope ignored = CorrelationContext.open(snapshot)) {
            assertThat(MDC.get("correlationId")).isEqualTo("corr-202");
            assertThat(MDC.get("traceId")).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
            assertThat(MDC.get("spanId")).isEqualTo("00f067aa0ba902b7");

            DomainEventMetadata metadata = CorrelationContext.currentMetadata();
            assertThat(metadata.correlationId()).isEqualTo("corr-202");
            assertThat(metadata.causationId()).isEqualTo("command-77");
            assertThat(metadata.trace()).isEqualTo(new TraceContext(
                "4bf92f3577b34da6a3ce929d0e0e4736",
                "00f067aa0ba902b7",
                true,
                TRACEPARENT,
                null,
                "ftgo.correlation_id=corr-202"
            ));
        }

        assertThat(MDC.get("correlationId")).isEqualTo("outer");
        assertThat(CorrelationContext.current()).isEmpty();
    }

    @Test
    void restoresContextFromDomainEventMetadata() {
        DomainEventMetadata metadata = new DomainEventMetadata(
            "corr-303",
            "event-12",
            new TraceContext(
                "4bf92f3577b34da6a3ce929d0e0e4736",
                "00f067aa0ba902b7",
                true,
                TRACEPARENT,
                "vendor=value",
                "tenant=t-303"
            )
        );

        CorrelationContext.Snapshot snapshot = CorrelationContext.fromMetadata(metadata);

        assertThat(snapshot.correlationId()).isEqualTo("corr-303");
        assertThat(snapshot.causationId()).isEqualTo("event-12");
        assertThat(snapshot.traceparent()).isEqualTo(TRACEPARENT);
        assertThat(snapshot.tracestate()).isEqualTo("vendor=value");
        assertThat(snapshot.baggage()).contains("tenant=t-303");
        assertThat(snapshot.baggage()).contains("ftgo.correlation_id=corr-303");
    }
}
