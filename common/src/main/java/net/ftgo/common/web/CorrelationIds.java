package net.ftgo.common.web;

import java.util.UUID;
import java.util.regex.Pattern;

/** Shared correlation ID contract without Servlet or WebFlux dependencies. */
public final class CorrelationIds {

    public static final String HEADER_NAME = "X-Correlation-ID";
    public static final String MDC_KEY = "correlationId";
    private static final Pattern SAFE_VALUE = Pattern.compile("[A-Za-z0-9._:-]{8,128}");

    private CorrelationIds() {
    }

    public static String normalizeOrGenerate(String candidate) {
        return isSafe(candidate) ? candidate : UUID.randomUUID().toString();
    }

    public static boolean isSafe(String candidate) {
        return candidate != null && SAFE_VALUE.matcher(candidate).matches();
    }
}
