package net.ftgo.gateway.filter;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Rejects non-JSON business mutations before route lookup and circuit breaking.
 *
 * <p>A client contract error must remain a stable 415 response even when the
 * downstream service is unavailable or its circuit breaker is open.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 30)
public final class JsonContentTypeFilter implements WebFilter {

    private static final String CORRELATION_HEADER = "X-Correlation-ID";
    private static final Pattern SAFE_CORRELATION_ID =
        Pattern.compile("[A-Za-z0-9._:-]{8,128}");
    private static final List<String> BUSINESS_PREFIXES = List.of(
        "/orders",
        "/consumers",
        "/restaurants",
        "/tickets",
        "/deliveries",
        "/order-history",
        "/order-details",
        "/accounts",
        "/admin/consumers",
        "/api/admin/payment-settlement"
    );

    private final JsonMapper jsonMapper;

    public JsonContentTypeFilter(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        HttpMethod method = exchange.getRequest().getMethod();
        String path = exchange.getRequest().getPath().pathWithinApplication().value();
        MediaType contentType = exchange.getRequest().getHeaders().getContentType();

        if (!isMutation(method)
            || !isBusinessPath(path)
            || contentType == null
            || isJson(contentType)) {
            return chain.filter(exchange);
        }

        return writeUnsupportedMediaType(exchange, path);
    }

    private Mono<Void> writeUnsupportedMediaType(ServerWebExchange exchange, String routedPath) {
        String correlationId = normalizeCorrelationId(
            exchange.getRequest().getHeaders().getFirst(CORRELATION_HEADER)
        );
        String instance = "1".equals(
            exchange.getResponse().getHeaders().getFirst(ApiVersioningFilter.VERSION_HEADER)
        ) ? ApiVersioningFilter.VERSION_PREFIX + routedPath : routedPath;

        Map<String, Object> problem = new LinkedHashMap<>();
        problem.put("type", "https://ftgo.example/problems/unsupported-media-type");
        problem.put("title", "Unsupported media type");
        problem.put("status", HttpStatus.UNSUPPORTED_MEDIA_TYPE.value());
        problem.put("detail", "Business mutation requests must use a JSON content type");
        problem.put("instance", instance);
        problem.put("errorCode", "UNSUPPORTED_MEDIA_TYPE");
        problem.put("correlationId", correlationId);

        byte[] bytes;
        try {
            bytes = jsonMapper.writeValueAsBytes(problem);
        } catch (JacksonException error) {
            return Mono.error(error);
        }

        exchange.getResponse().setStatusCode(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        HttpHeaders headers = exchange.getResponse().getHeaders();
        headers.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        headers.set(CORRELATION_HEADER, correlationId);
        return exchange.getResponse().writeWith(Mono.just(
            exchange.getResponse().bufferFactory().wrap(bytes)
        ));
    }

    private boolean isMutation(HttpMethod method) {
        return method == HttpMethod.POST
            || method == HttpMethod.PUT
            || method == HttpMethod.PATCH;
    }

    private boolean isBusinessPath(String path) {
        return BUSINESS_PREFIXES.stream().anyMatch(prefix ->
            path.equals(prefix) || path.startsWith(prefix + "/")
        );
    }

    private boolean isJson(MediaType contentType) {
        String subtype = contentType.getSubtype().toLowerCase(Locale.ROOT);
        return "json".equals(subtype) || subtype.endsWith("+json");
    }

    private String normalizeCorrelationId(String candidate) {
        if (candidate != null && SAFE_CORRELATION_ID.matcher(candidate).matches()) {
            return candidate;
        }
        return UUID.randomUUID().toString();
    }
}
