package net.ftgo.order.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.ftgo.common.Address;
import net.ftgo.common.Money;
import net.ftgo.common.security.FtgoJwtAuthenticationConverter;
import net.ftgo.order.config.SecurityConfiguration;
import net.ftgo.order.idempotency.IdempotencyKeyConflictException;
import net.ftgo.order.idempotency.IdempotentResult;
import net.ftgo.order.idempotency.OrderMutationIdempotencyService;
import net.ftgo.order.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(properties = {
    "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://identity.example/realms/ftgo",
    "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://identity.example/realms/ftgo/protocol/openid-connect/certs"
})
@ContextConfiguration(classes = {
    SecurityConfiguration.class,
    OrderController.class,
    OrderApiExceptionHandler.class
})
class OrderControllerIdempotencyTest {

    private static final Long CONSUMER_ID = 101L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrderController orderController;

    @MockBean
    private OrderService orderService;

    @MockBean
    private OrderMutationIdempotencyService idempotencyService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(orderController, "phase2Enabled", true);
    }

    @Test
    void createRequiresIdempotencyKey() throws Exception {
        mockMvc.perform(post("/orders")
                .with(authentication(consumerAuthentication()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validCreateRequest())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("IDEMPOTENCY_KEY_REQUIRED"));

        verifyNoInteractions(orderService, idempotencyService);
    }

    @Test
    void createReplaysTheExactStoredResponse() throws Exception {
        CreateOrderRequest request = validCreateRequest();
        byte[] hash = new byte[] {1, 2, 3};
        String responseJson = "{\"orderId\":9001}";
        when(idempotencyService.hashCreate(eq(CONSUMER_ID), any(CreateOrderRequest.class)))
            .thenReturn(hash);
        when(idempotencyService.execute(
            eq(CONSUMER_ID),
            eq("CREATE_ORDER"),
            eq("create-101-1"),
            eq(hash),
            any()
        )).thenReturn(new IdempotentResult<>(201, responseJson, 9001L, true));

        mockMvc.perform(post("/orders")
                .header("Idempotency-Key", "create-101-1")
                .with(authentication(consumerAuthentication()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(content().json(responseJson));
    }

    @Test
    void reusedKeyWithChangedPayloadReturnsConflict() throws Exception {
        CreateOrderRequest request = validCreateRequest();
        byte[] hash = new byte[] {4, 5, 6};
        when(idempotencyService.hashCreate(eq(CONSUMER_ID), any(CreateOrderRequest.class)))
            .thenReturn(hash);
        when(idempotencyService.execute(
            anyLong(),
            eq("CREATE_ORDER"),
            eq("create-101-2"),
            eq(hash),
            any()
        )).thenThrow(new IdempotencyKeyConflictException());

        mockMvc.perform(post("/orders")
                .header("Idempotency-Key", "create-101-2")
                .with(authentication(consumerAuthentication()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.errorCode").value("IDEMPOTENCY_KEY_CONFLICT"));
    }

    @Test
    void cancelAndReviseRequireIdempotencyKey() throws Exception {
        mockMvc.perform(post("/orders/{orderId}/cancel", 9001L)
                .with(authentication(consumerAuthentication())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("IDEMPOTENCY_KEY_REQUIRED"));

        ReviseOrderRequest revise = new ReviseOrderRequest(List.of(
            new OrderLineItemRequest(1L, "Pizza", new Money("20.00"), 2)
        ));
        mockMvc.perform(post("/orders/{orderId}/revise", 9001L)
                .with(authentication(consumerAuthentication()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(revise)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("IDEMPOTENCY_KEY_REQUIRED"));
    }

    private CreateOrderRequest validCreateRequest() {
        return new CreateOrderRequest(
            456L,
            7L,
            List.of(new OrderLineItemRequest(1L, "Pizza", new Money("20.00"), 1)),
            new Address("123 Main St", "San Francisco", "CA", "94102"),
            LocalDateTime.now().plusHours(2),
            "tok_visa_4242"
        );
    }

    private AbstractAuthenticationToken consumerAuthentication() {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("consumer-token")
            .header("alg", "RS256")
            .subject("consumer-101")
            .issuedAt(now)
            .expiresAt(now.plusSeconds(300))
            .audience(List.of("ftgo-api"))
            .claim("consumer_id", CONSUMER_ID)
            .claim("roles", List.of("CONSUMER"))
            .build();
        return new FtgoJwtAuthenticationConverter("").convert(jwt);
    }
}
