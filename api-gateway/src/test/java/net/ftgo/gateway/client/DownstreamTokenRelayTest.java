package net.ftgo.gateway.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import net.ftgo.common.security.FtgoJwtAuthenticationConverter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

class DownstreamTokenRelayTest {

    private static final String TOKEN_VALUE = "signed-consumer-token";

    private WireMockServer wireMock;
    private OrderServiceClient orderClient;
    private KitchenServiceClient kitchenClient;
    private DeliveryServiceClient deliveryClient;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(0);
        wireMock.start();
        String baseUrl = wireMock.baseUrl();
        WebClient.Builder builder = WebClient.builder();
        orderClient = new OrderServiceClient(builder, baseUrl);
        kitchenClient = new KitchenServiceClient(builder, baseUrl);
        deliveryClient = new DeliveryServiceClient(builder, baseUrl);
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void relaysFtgoJwtToEveryComposedDownstreamCall() {
        wireMock.stubFor(get(urlEqualTo("/orders/123")).willReturn(okJson("{}")));
        wireMock.stubFor(get(urlEqualTo("/tickets/by-order/123")).willReturn(okJson("{}")));
        wireMock.stubFor(get(urlEqualTo("/deliveries/by-order/123")).willReturn(okJson("{}")));

        AbstractAuthenticationToken authentication = consumerAuthentication();

        StepVerifier.create(orderClient.getOrder(123L)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication)))
            .expectNextCount(1)
            .verifyComplete();
        StepVerifier.create(kitchenClient.getTicketByOrderId(123L)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication)))
            .expectNextCount(1)
            .verifyComplete();
        StepVerifier.create(deliveryClient.getDeliveryByOrderId(123L)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication)))
            .expectNextCount(1)
            .verifyComplete();

        String authorization = "Bearer " + TOKEN_VALUE;
        wireMock.verify(getRequestedFor(urlEqualTo("/orders/123"))
            .withHeader("Authorization", equalTo(authorization)));
        wireMock.verify(getRequestedFor(urlEqualTo("/tickets/by-order/123"))
            .withHeader("Authorization", equalTo(authorization)));
        wireMock.verify(getRequestedFor(urlEqualTo("/deliveries/by-order/123"))
            .withHeader("Authorization", equalTo(authorization)));
    }

    private AbstractAuthenticationToken consumerAuthentication() {
        Instant now = Instant.parse("2026-07-27T00:00:00Z");
        Jwt jwt = Jwt.withTokenValue(TOKEN_VALUE)
            .header("alg", "RS256")
            .subject("consumer-101")
            .issuedAt(now)
            .expiresAt(now.plusSeconds(300))
            .audience(List.of("ftgo-api"))
            .claim("consumer_id", 101L)
            .claim("roles", List.of("CONSUMER"))
            .build();
        return new FtgoJwtAuthenticationConverter("").convert(jwt);
    }
}
