package net.ftgo.common.observability;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Establishes a bounded correlation scope for every servlet request and
 * returns the canonical identifiers to the caller.
 */
public final class CorrelationContextFilter implements Filter {

    @Override
    public void doFilter(
        ServletRequest servletRequest,
        ServletResponse servletResponse,
        FilterChain chain
    ) throws IOException, ServletException {
        if (!(servletRequest instanceof HttpServletRequest request)
            || !(servletResponse instanceof HttpServletResponse response)) {
            chain.doFilter(servletRequest, servletResponse);
            return;
        }

        CorrelationContext.Snapshot snapshot = CorrelationContext.resolve(headers(request));
        request.setAttribute(CorrelationContext.CORRELATION_ID, snapshot.correlationId());
        request.setAttribute(CorrelationContext.TRACEPARENT, snapshot.traceparent());
        CorrelationContext.inject(snapshot, response::setHeader);

        try (CorrelationContext.Scope ignored = CorrelationContext.open(snapshot)) {
            chain.doFilter(request, response);
        }
    }

    private Map<String, String> headers(HttpServletRequest request) {
        Map<String, String> values = new LinkedHashMap<>();
        copyHeader(request, values, CorrelationContext.TRACEPARENT);
        copyHeader(request, values, CorrelationContext.TRACESTATE);
        copyHeader(request, values, CorrelationContext.BAGGAGE);
        copyHeader(request, values, CorrelationContext.CORRELATION_ID);
        copyHeader(request, values, CorrelationContext.CAUSATION_ID);
        return values;
    }

    private void copyHeader(
        HttpServletRequest request,
        Map<String, String> values,
        String name
    ) {
        String value = request.getHeader(name);
        if (value != null) {
            values.put(name, value);
        }
    }
}
