package net.ftgo.order.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.ftgo.common.Address;
import net.ftgo.common.Money;
import net.ftgo.common.security.FtgoJwtAuthenticationConverter;
import net.ftgo.common.security.FtgoPrincipal;
import net.ftgo.order.config.SecurityConfiguration;
import net.ftgo.order.domain.DeliveryInfo;
import net.ftgo.order.domain.Order;
import net.ftgo.order.domain.OrderLineItem;
import net.ftgo.order.domain.PaymentInfo;
import net.ftgo.order.idempotency.IdempotentResult;
import net.ftgo.order.idempotency.OrderMutationIdempotencyService;
import net.ftgo.order.service.OrderNotFoundException;
import net.ftgo.order.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class OrderControllerTest {

    private static final Long CONSUMER_ID = 123L;
    private static final String IDEMPOTENCY_KEY = "order-controller-test-key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrderController orderController;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private OrderMutationIdempotencyService idempotencyService;

    private Order sampleOrder;

    @BeforeEach
    void setUp() {
        sampleOrder = createSampleOrder();
        ReflectionTestUtils.setField(sampleOrder, "id", 1L);
        ReflectionTestUtils.setField(orderController, "phase2Enabled", true);

        when(idempotencyService.hashCreate(anyLong(), any(CreateOrderRequest.class)))
            .thenReturn(new byte[] {1});
        when(idempotencyService.hashCancel(anyLong(), anyLong()))
            .thenReturn(new byte[] {2});
        when(idempotencyService.hashRevise(
            anyLong(),
            anyLong(),
            any(ReviseOrderRequest.class)
        )).thenReturn(new byte[] {3});
        when(idempotencyService.execute(
            anyLong(),
            anyString(),
            anyString(),
            any(byte[].class),
            org.mockito.ArgumentMatchers.<Supplier<String>>any()
        )).thenAnswer(invocation -> {
            String operation = invocation.getArgument(1);
            Supplier<String> mutation = invocation.getArgument(4);
            String response = mutation.get();
            int status = OrderMutationIdempotencyService.CREATE_ORDER.equals(operation)
                ? 201
                : 200;
            return new IdempotentResult<>(status, response, null, false);
        });
    }

    @Test
    void createOrderPropagatesExpectedMenuVersion() throws Exception {
        CreateOrderRequest request = createValidOrderRequest(7L);
        when(orderService.createOrder(
            eq(CONSUMER_ID),
            eq(456L),
            eq(7L),
            anyList(),
            any(DeliveryInfo.class),
            any(PaymentInfo.class)
        )).thenReturn(1L);

        mockMvc.perform(post("/orders")
                .header("Idempotency-Key", IDEMPOTENCY_KEY)
                .with(authentication(consumerAuthentication(CONSUMER_ID)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.orderId").value(1L));

        verify(orderService).createOrder(
            eq(CONSUMER_ID),
            eq(456L),
            eq(7L),
            anyList(),
            any(DeliveryInfo.class),
            any(PaymentInfo.class)
        );
    }

    @Test
    void disabledPhase02OrderIntakeReturnsServiceUnavailable() throws Exception {
        ReflectionTestUtils.setField(orderController, "phase2Enabled", false);

        mockMvc.perform(post("/orders")
                .with(authentication(consumerAuthentication(CONSUMER_ID)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createValidOrderRequest(7L))))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.errorCode").value("ORDER_FLOW_DISABLED"));

        verifyNoInteractions(orderService);
    }

    @Test
    void createOrderDefaultsMissingMenuVersionToZero() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(
            456L,
            createLineItemRequests(),
            createSampleAddress(),
            LocalDateTime.now().plusHours(1),
            "tok_visa_4242"
        );
        when(orderService.createOrder(
            eq(CONSUMER_ID),
            eq(456L),
            eq(0L),
            anyList(),
            any(DeliveryInfo.class),
            any(PaymentInfo.class)
        )).thenReturn(2L);

        mockMvc.perform(post("/orders")
                .header("Idempotency-Key", IDEMPOTENCY_KEY)
                .with(authentication(consumerAuthentication(CONSUMER_ID)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.orderId").value(2L));
    }

    @Test
    void invalidCreateRequestReturnsBadRequest() throws Exception {
        CreateOrderRequest invalid = new CreateOrderRequest(
            null,
            0L,
            createLineItemRequests(),
            createSampleAddress(),
            LocalDateTime.now().plusHours(1),
            "tok_visa_4242"
        );

        mockMvc.perform(post("/orders")
                .header("Idempotency-Key", IDEMPOTENCY_KEY)
                .with(authentication(consumerAuthentication(CONSUMER_ID)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalid)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void getOrderReturnsOrder() throws Exception {
        when(orderService.getOrder(eq(1L), any(FtgoPrincipal.class))).thenReturn(sampleOrder);

        mockMvc.perform(get("/orders/{orderId}", 1L)
                .with(authentication(consumerAuthentication(CONSUMER_ID))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1L))
            .andExpect(jsonPath("$.state").value("APPROVAL_PENDING"));
    }

    @Test
    void getUnknownOrderReturnsNotFound() throws Exception {
        when(orderService.getOrder(eq(404L), any(FtgoPrincipal.class)))
            .thenThrow(new OrderNotFoundException("Order not found: 404"));

        mockMvc.perform(get("/orders/{orderId}", 404L)
                .with(authentication(consumerAuthentication(CONSUMER_ID))))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("ORDER_NOT_FOUND"));
    }

    @Test
    void cancelOrderDelegatesToService() throws Exception {
        doNothing().when(orderService).cancelOrder(eq(1L), any(FtgoPrincipal.class));

        mockMvc.perform(post("/orders/{orderId}/cancel", 1L)
                .header("Idempotency-Key", IDEMPOTENCY_KEY)
                .with(authentication(consumerAuthentication(CONSUMER_ID))))
            .andExpect(status().isOk());

        verify(orderService).cancelOrder(eq(1L), any(FtgoPrincipal.class));
    }

    @Test
    void pendingCancelReturnsConflict() throws Exception {
        doThrow(new IllegalStateException(
            "Cannot modify order in state CONFIRMATION_PENDING. Operation in progress"
        )).when(orderService).cancelOrder(eq(1L), any(FtgoPrincipal.class));

        mockMvc.perform(post("/orders/{orderId}/cancel", 1L)
                .header("Idempotency-Key", IDEMPOTENCY_KEY)
                .with(authentication(consumerAuthentication(CONSUMER_ID))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.errorCode").value("CONFLICT"));
    }

    @Test
    void reviseOrderDelegatesToService() throws Exception {
        ReviseOrderRequest request = new ReviseOrderRequest(List.of(
            new OrderLineItemRequest(1L, "Pizza", new Money("20.00"), 2)
        ));
        doNothing().when(orderService).reviseOrder(
            eq(1L),
            anyList(),
            any(FtgoPrincipal.class)
        );

        mockMvc.perform(post("/orders/{orderId}/revise", 1L)
                .header("Idempotency-Key", IDEMPOTENCY_KEY)
                .with(authentication(consumerAuthentication(CONSUMER_ID)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk());

        verify(orderService).reviseOrder(
            eq(1L),
            anyList(),
            any(FtgoPrincipal.class)
        );
    }

    @Test
    void pendingRevisionReturnsConflict() throws Exception {
        ReviseOrderRequest request = new ReviseOrderRequest(List.of(
            new OrderLineItemRequest(1L, "Pizza", new Money("20.00"), 1)
        ));
        doThrow(new IllegalStateException(
            "Cannot modify order in state REJECTION_PENDING. Operation in progress"
        )).when(orderService).reviseOrder(
            eq(1L),
            anyList(),
            any(FtgoPrincipal.class)
        );

        mockMvc.perform(post("/orders/{orderId}/revise", 1L)
                .header("Idempotency-Key", IDEMPOTENCY_KEY)
                .with(authentication(consumerAuthentication(CONSUMER_ID)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.errorCode").value("CONFLICT"));
    }

    private CreateOrderRequest createValidOrderRequest(Long menuVersion) {
        return new CreateOrderRequest(
            456L,
            menuVersion,
            createLineItemRequests(),
            createSampleAddress(),
            LocalDateTime.now().plusHours(1),
            "tok_visa_4242"
        );
    }

    private List<OrderLineItemRequest> createLineItemRequests() {
        return List.of(
            new OrderLineItemRequest(1L, "Burger", new Money(new BigDecimal("10.00")), 2),
            new OrderLineItemRequest(2L, "Fries", new Money(new BigDecimal("15.00")), 3)
        );
    }

    private Address createSampleAddress() {
        return new Address("123 Main St", "San Francisco", "CA", "94102");
    }

    private Order createSampleOrder() {
        return new Order(
            CONSUMER_ID,
            456L,
            List.of(
                new OrderLineItem(1L, "Burger", new Money("10.00"), 2),
                new OrderLineItem(2L, "Fries", new Money("15.00"), 3)
            ),
            new DeliveryInfo(
                "123 Main St, San Francisco, CA 94102",
                LocalDateTime.now().plusHours(1)
            ),
            new PaymentInfo("tok_visa_4242")
        );
    }

    private AbstractAuthenticationToken consumerAuthentication(Long consumerId) {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("consumer-token")
            .header("alg", "RS256")
            .subject("consumer-" + consumerId)
            .issuedAt(now)
            .expiresAt(now.plusSeconds(300))
            .audience(List.of("ftgo-api"))
            .claim("consumer_id", consumerId)
            .claim("roles", List.of("CONSUMER"))
            .build();
        return new FtgoJwtAuthenticationConverter("").convert(jwt);
    }
}
