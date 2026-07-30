package net.ftgo.gateway.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.ftgo.common.web.CorrelationIds;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

import static org.assertj.core.api.Assertions.assertThat;

class GatewaySecurityProblemDetailTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void authenticationFailureUsesCorrelatedRfc9457Response() throws Exception {
        SecurityConfiguration configuration = new SecurityConfiguration();
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/orders/1")
                .header(CorrelationIds.HEADER_NAME, "corr-auth-1234")
        );

        configuration.problemAuthenticationEntryPoint()
            .commence(exchange, new BadCredentialsException("raw token detail"))
            .block();

        String responseBody = exchange.getResponse().getBodyAsString().block();
        JsonNode body = JSON.readTree(responseBody);
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getHeaders().getContentType())
            .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(body.path("type").asText()).isEqualTo("https://ftgo.example/problems/unauthorized");
        assertThat(body.path("title").asText()).isEqualTo("Authentication required");
        assertThat(body.path("status").asInt()).isEqualTo(401);
        assertThat(body.path("detail").asText()).isEqualTo("Authentication is required to access this resource");
        assertThat(body.path("instance").asText()).isEqualTo("/orders/1");
        assertThat(body.path("errorCode").asText()).isEqualTo("UNAUTHORIZED");
        assertThat(body.path("correlationId").asText()).isEqualTo("corr-auth-1234");
        assertThat(exchange.getResponse().getHeaders().getFirst("WWW-Authenticate"))
            .contains("Bearer");
        assertThat(responseBody).doesNotContain("raw token detail");
    }

    @Test
    void authorizationFailureUsesCorrelatedRfc9457Response() throws Exception {
        SecurityConfiguration configuration = new SecurityConfiguration();
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/admin/consumers/1")
                .header(CorrelationIds.HEADER_NAME, "corr-denied-1234")
        );

        configuration.problemAccessDeniedHandler()
            .handle(exchange, new AccessDeniedException("sensitive policy detail"))
            .block();

        String responseBody = exchange.getResponse().getBodyAsString().block();
        JsonNode body = JSON.readTree(responseBody);
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(exchange.getResponse().getHeaders().getContentType())
            .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(body.path("errorCode").asText()).isEqualTo("FORBIDDEN");
        assertThat(body.path("detail").asText()).isEqualTo("Access to this resource is forbidden");
        assertThat(body.path("correlationId").asText()).isEqualTo("corr-denied-1234");
        assertThat(responseBody).doesNotContain("sensitive policy detail");
    }
}
