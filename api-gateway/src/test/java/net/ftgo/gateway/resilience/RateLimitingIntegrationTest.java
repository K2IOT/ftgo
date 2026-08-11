package net.ftgo.gateway.resilience;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import net.ftgo.gateway.GatewayTestSecurityConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockUser;

/**
 * Integration tests for the Redis token-bucket rate limiter used by API Gateway.
 */
@SpringBootTest
@AutoConfigureWebTestClient
@Testcontainers
@Import(GatewayTestSecurityConfiguration.class)
class RateLimitingIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    private static WireMockServer wireMockServer;

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
        .withExposedPorts(6379);

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(18091);
        wireMockServer.start();
        WireMock.configureFor("localhost", 18091);
        stubFor(get(urlPathMatching("/.*"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"status\":\"ok\"}")));
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);

        // A complete test route avoids partially overriding a production route definition.
        // replenishRate=1 token/s, requestedTokens=2, burstCapacity=4 => burst of 2
        // requests and one new request every 2 seconds.
        String route = "spring.cloud.gateway.server.webflux.routes[0]";
        registry.add(route + ".id", () -> "rate-limit-test");
        registry.add(route + ".uri", () -> "http://localhost:18091");
        registry.add(route + ".predicates[0]", () -> "Path=/rate-limit-test/**");
        registry.add(route + ".filters[0].name", () -> "RequestRateLimiter");
        registry.add(route + ".filters[0].args.redis-rate-limiter.replenishRate", () -> "1");
        registry.add(route + ".filters[0].args.redis-rate-limiter.burstCapacity", () -> "4");
        registry.add(route + ".filters[0].args.redis-rate-limiter.requestedTokens", () -> "2");
        registry.add(route + ".filters[0].args.key-resolver", () -> "#{@userKeyResolver}");
        registry.add(route + ".filters[0].args.deny-empty-key", () -> "false");
    }

    @Test
    void rejectsRequestAfterConfiguredBurstIsExhausted() {
        expectAllowed("burst-user");
        expectAllowed("burst-user");
        expectLimited("burst-user");
    }

    @Test
    void rateLimitIsIsolatedPerUser() {
        expectAllowed("user-one");
        expectAllowed("user-one");
        expectLimited("user-one");
        expectAllowed("user-two");
    }

    @Test
    void tokensAreReplenishedOverTime() throws InterruptedException {
        expectAllowed("replenish-user");
        expectAllowed("replenish-user");
        expectLimited("replenish-user");

        Thread.sleep(2200);

        expectAllowed("replenish-user");
    }

    @Test
    void responseIncludesRateLimitHeaders() {
        webTestClient.mutateWith(mockUser("headers-user")).get()
            .uri("/rate-limit-test/ping")
            .exchange()
            .expectStatus().isOk()
            .expectHeader().exists("X-RateLimit-Remaining")
            .expectHeader().exists("X-RateLimit-Replenish-Rate")
            .expectHeader().exists("X-RateLimit-Burst-Capacity")
            .expectHeader().exists("X-RateLimit-Requested-Tokens");
    }

    @Test
    void configuredBurstAllowsTwoImmediateRequests() {
        expectAllowed("capacity-user");
        expectAllowed("capacity-user");
        verify(exactly(2), getRequestedFor(urlPathMatching("/rate-limit-test/.*")));
    }

    private void expectAllowed(String userId) {
        webTestClient.mutateWith(mockUser(userId)).get()
            .uri("/rate-limit-test/ping")
            .exchange()
            .expectStatus().isOk();
    }

    private void expectLimited(String userId) {
        webTestClient.mutateWith(mockUser(userId)).get()
            .uri("/rate-limit-test/ping")
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }
}
