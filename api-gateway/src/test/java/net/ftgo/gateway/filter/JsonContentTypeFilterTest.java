package net.ftgo.gateway.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class JsonContentTypeFilterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ApiVersioningFilter versioningFilter = new ApiVersioningFilter();
    private final JsonContentTypeFilter contentTypeFilter = new JsonContentTypeFilter(objectMapper);

    @Test
    void rejectsVersionedTextMutationBeforeRouting() throws Exception {
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.post("/api/v1/orders")
                .contentType(MediaType.TEXT_PLAIN)
                .body("not-json")
        );
        AtomicBoolean routed = new AtomicBoolean();

        versioningFilter.filter(exchange, versioned ->
            contentTypeFilter.filter(versioned, candidate -> {
                routed.set(true);
                return Mono.empty();
            })
        ).block();

        assertThat(routed).isFalse();
        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(415);
        assertThat(exchange.getResponse().getHeaders().getContentType())
            .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(exchange.getResponse().getHeaders().getFirst("X-API-Version")).isEqualTo("1");
        assertThat(exchange.getResponse().getHeaders().getFirst("X-Correlation-ID")).isNotBlank();

        String body = exchange.getResponse().getBodyAsString().block();
        JsonNode problem = objectMapper.readTree(body.getBytes(StandardCharsets.UTF_8));
        assertThat(problem.path("status").asInt()).isEqualTo(415);
        assertThat(problem.path("errorCode").asText()).isEqualTo("UNSUPPORTED_MEDIA_TYPE");
        assertThat(problem.path("instance").asText()).isEqualTo("/api/v1/orders");
        assertThat(problem.path("correlationId").asText()).isNotBlank();
    }

    @Test
    void allowsJsonMutationToContinue() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.post("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{}")
        );
        AtomicBoolean routed = new AtomicBoolean();

        contentTypeFilter.filter(exchange, candidate -> {
            routed.set(true);
            return candidate.getResponse().setComplete();
        }).block();

        assertThat(routed).isTrue();
    }

    @Test
    void allowsStructuredJsonMutationToContinue() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.patch("/consumers/41")
                .contentType(MediaType.parseMediaType("application/merge-patch+json"))
                .body("{}")
        );
        AtomicBoolean routed = new AtomicBoolean();

        contentTypeFilter.filter(exchange, candidate -> {
            routed.set(true);
            return candidate.getResponse().setComplete();
        }).block();

        assertThat(routed).isTrue();
    }

    @Test
    void doesNotRequireContentTypeForBodylessMutationOrRead() {
        assertDelegates(MockServerHttpRequest.post("/orders/41/cancel").build());
        assertDelegates(MockServerHttpRequest.get("/orders/41")
            .header("Content-Type", MediaType.TEXT_PLAIN_VALUE)
            .build());
    }

    private void assertDelegates(MockServerHttpRequest request) {
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicBoolean routed = new AtomicBoolean();

        contentTypeFilter.filter(exchange, candidate -> {
            routed.set(true);
            return candidate.getResponse().setComplete();
        }).block();

        assertThat(routed).isTrue();
    }
}
