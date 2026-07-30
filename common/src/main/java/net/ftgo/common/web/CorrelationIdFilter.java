package net.ftgo.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/** Normalizes a safe request correlation ID and propagates it to logs/responses. */
public final class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Correlation-ID";
    public static final String ATTRIBUTE_NAME = CorrelationIdFilter.class.getName() + ".value";
    public static final String MDC_KEY = "correlationId";
    private static final Pattern SAFE_VALUE = Pattern.compile("[A-Za-z0-9._:-]{8,128}");

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String correlationId = normalizeOrGenerate(request.getHeader(HEADER_NAME));
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
        if (value instanceof String correlationId && isSafe(correlationId)) {
            return correlationId;
        }
        String correlationId = normalizeOrGenerate(request.getHeader(HEADER_NAME));
        request.setAttribute(ATTRIBUTE_NAME, correlationId);
        return correlationId;
    }

    public static String normalizeOrGenerate(String candidate) {
        return isSafe(candidate) ? candidate : UUID.randomUUID().toString();
    }

    public static boolean isSafe(String candidate) {
        return candidate != null && SAFE_VALUE.matcher(candidate).matches();
    }
}
