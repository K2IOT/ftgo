package net.ftgo.common.observability;

import net.ftgo.common.messaging.DomainEventMetadata;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationContextFilterTest {

    private static final String TRACEPARENT =
        "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void opensRequestScopePropagatesHeadersAndRestoresThreadState() throws Exception {
        CorrelationContextFilter filter = new CorrelationContextFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
        request.addHeader(CorrelationContext.TRACEPARENT, TRACEPARENT);
        request.addHeader(CorrelationContext.TRACESTATE, "vendor=value");
        request.addHeader(CorrelationContext.BAGGAGE, "tenant=t-101");
        request.addHeader(CorrelationContext.CORRELATION_ID, "corr-http-101");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain((servletRequest, servletResponse) -> {
            DomainEventMetadata metadata = CorrelationContext.currentMetadata();
            assertThat(metadata.correlationId()).isEqualTo("corr-http-101");
            assertThat(metadata.trace().traceparent()).isEqualTo(TRACEPARENT);
            assertThat(MDC.get("correlationId")).isEqualTo("corr-http-101");
            assertThat(servletRequest.getAttribute(CorrelationContext.CORRELATION_ID))
                .isEqualTo("corr-http-101");
        });

        MDC.put("correlationId", "outer");
        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(CorrelationContext.CORRELATION_ID))
            .isEqualTo("corr-http-101");
        assertThat(response.getHeader(CorrelationContext.TRACEPARENT)).isEqualTo(TRACEPARENT);
        assertThat(MDC.get("correlationId")).isEqualTo("outer");
        assertThat(CorrelationContext.current()).isEmpty();
    }
}
