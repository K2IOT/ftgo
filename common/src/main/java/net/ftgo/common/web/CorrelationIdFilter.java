package net.ftgo.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Normalizes a safe request correlation ID and propagates it to logs/responses. */
public final class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = CorrelationIds.HEADER_NAME;
    public static final String ATTRIBUTE_NAME = CorrelationIdFilter.class.getName() + ".value";
    public static final String MDC_KEY = CorrelationIds.MDC_KEY;

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String correlationId = CorrelationIds.normalizeOrGenerate(request.getHeader(HEADER_NAME));
        request.setAttribute(ATTRIBUTE_NAME, correlationId);
        response.setHeader(HEADER_NAME, correlationId);
        MDC.put(MDC_KEY, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    public static String current(HttpServletRequest request) {
        Object value = request.getAttribute(ATTRIBUTE_NAME);
        if (value instanceof String correlationId && CorrelationIds.isSafe(correlationId)) {
            return correlationId;
        }
        String correlationId = CorrelationIds.normalizeOrGenerate(request.getHeader(HEADER_NAME));
        request.setAttribute(ATTRIBUTE_NAME, correlationId);
        return correlationId;
    }
}
