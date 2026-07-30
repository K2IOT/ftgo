package net.ftgo.gateway.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.ftgo.common.web.CorrelationIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.client.WebClientRequestException;

import java.net.URI;
import java.net.http.HttpRequest;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayProblemDetailContractTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void downstreamConnectionFailureUsesStableRfc9457Response() throws Exception {
        GlobalErrorHandler handler = new GlobalErrorHandler();
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/orders/42")
                .header(CorrelationIdFilter.HEADER_NAME, "corr-gateway-1234")
        );
        WebClientRequestException error = new WebClientRequestException(
            new RuntimeException("jdbc:mysql://secret-host/password"),
            HttpRequest.newBuilder(URI.create("http://order-service/orders/42")).GET().build().method(),
            URI.create("http://order-service/orders/42"),
            org.springframework.http.HttpHeaders.EMPTY
        );

        handler.handle(exchange, error).block();

        JsonNode body = JSON.readTree(exchange.getResponse().getBodyAsString().block());
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(exchange.getResponse().getHeaders().getContentType())
            .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(body.path("type").asText()).isEqualTo("https://ftgo.example/problems/service-unavailable");
        assertThat(body.path("title").asText()).isEqualTo("Downstream service unavailable");
        assertThat(body.path("status").asInt()).isEqualTo(502);
        assertThat(body.path("detail").asText()).isEqualTo("The downstream service is unavailable");
        assertThat(body.path("instance").asText()).isEqualTo("/orders/42");
        assertThat(body.path("errorCode").asText()).isEqualTo("SERVICE_UNAVAILABLE");
        assertThat(body.path("correlationId").asText()).isEqualTo("corr-gateway-1234");
        assertThat(exchange.getResponse().getBodyAsString().block())
            .doesNotContain("secret-host")
            .doesNotContain("RuntimeException");
    }

    @Test
    void unexpectedFailureDoesNotLeakRawExceptionMessage() throws Exception {
        GlobalErrorHandler handler = new GlobalErrorHandler();
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/orders/42")
        );

        handler.handle(exchange, new IllegalStateException("payment-token-secret")).block();

        JsonNode body = JSON.readTree(exchange.getResponse().getBodyAsString().block());
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(body.path("errorCode").asText()).isEqualTo("INTERNAL_ERROR");
        assertThat(body.path("detail").asText()).isEqualTo("An unexpected error occurred");
        assertThat(body.path("correlationId").asText()).matches("[A-Za-z0-9._:-]{8,128}");
        assertThat(exchange.getResponse().getBodyAsString().block())
            .doesNotContain("payment-token-secret")
            .doesNotContain("IllegalStateException");
    }
}
