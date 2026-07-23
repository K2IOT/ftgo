package net.ftgo.order.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.ftgo.common.Address;
import net.ftgo.common.Money;
import net.ftgo.order.domain.DeliveryInfo;
import net.ftgo.order.domain.Order;
import net.ftgo.order.domain.OrderLineItem;
import net.ftgo.order.domain.PaymentInfo;
import net.ftgo.order.service.OrderNotFoundException;
import net.ftgo.order.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderService orderService;

    private Order sampleOrder;

    @BeforeEach
    void setUp() {
        sampleOrder = createSampleOrder();
        ReflectionTestUtils.setField(sampleOrder, "id", 1L);
    }

    @Test
    void createOrderPropagatesExpectedMenuVersion() throws Exception {
        CreateOrderRequest request = createValidOrderRequest(7L);
        when(orderService.createOrder(
            eq(123L),
            eq(456L),
            eq(7L),
            anyList(),
            any(DeliveryInfo.class),
            any(PaymentInfo.class)
        )).thenReturn(1L);

        mockMvc.perform(post("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.orderId").value(1L));

        verify(orderService).createOrder(
            eq(123L),
            eq(456L),
            eq(7L),
            anyList(),
            any(DeliveryInfo.class),
            any(PaymentInfo.class)
        );
    }

    @Test
    void createOrderDefaultsMissingMenuVersionToZero() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(
            123L,
            456L,
            createLineItemRequests(),
            createSampleAddress(),
            LocalDateTime.now().plusHours(1),
            "tok_visa_4242"
        );
        when(orderService.createOrder(
            eq(123L),
            eq(456L),
            eq(0L),
            anyList(),
            any(DeliveryInfo.class),
            any(PaymentInfo.class)
        )).thenReturn(2L);

        mockMvc.perform(post("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.orderId").value(2L));
    }

    @Test
    void invalidCreateRequestReturnsBadRequest() throws Exception {
        CreateOrderRequest invalid = new CreateOrderRequest(
            null,
            456L,
            0L,
            createLineItemRequests(),
            createSampleAddress(),
            LocalDateTime.now().plusHours(1),
            "tok_visa_4242"
        );

        mockMvc.perform(post("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalid)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void getOrderReturnsOrder() throws Exception {
        when(orderService.getOrder(1L)).thenReturn(sampleOrder);

        mockMvc.perform(get("/orders/{orderId}", 1L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1L))
            .andExpect(jsonPath("$.state").value("APPROVAL_PENDING"));
    }

    @Test
    void getUnknownOrderReturnsNotFound() throws Exception {
        when(orderService.getOrder(404L))
            .thenThrow(new OrderNotFoundException("Order not found: 404"));

        mockMvc.perform(get("/orders/{orderId}", 404L))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("ORDER_NOT_FOUND"));
    }

    @Test
    void cancelOrderDelegatesToService() throws Exception {
        doNothing().when(orderService).cancelOrder(1L);

        mockMvc.perform(post("/orders/{orderId}/cancel", 1L))
            .andExpect(status().isOk());

        verify(orderService).cancelOrder(1L);
    }

    @Test
    void pendingCancelReturnsConflict() throws Exception {
        doThrow(new IllegalStateException(
            "Cannot modify order in state CONFIRMATION_PENDING. Operation in progress"
        )).when(orderService).cancelOrder(1L);

        mockMvc.perform(post("/orders/{orderId}/cancel", 1L))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.errorCode").value("CONFLICT"));
    }

    @Test
    void reviseOrderDelegatesToService() throws Exception {
        ReviseOrderRequest request = new ReviseOrderRequest(List.of(
            new OrderLineItemRequest(1L, "Pizza", new Money("20.00"), 2)
        ));
        doNothing().when(orderService).reviseOrder(eq(1L), anyList());

        mockMvc.perform(post("/orders/{orderId}/revise", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk());

        verify(orderService).reviseOrder(eq(1L), anyList());
    }

    @Test
    void pendingRevisionReturnsConflict() throws Exception {
        ReviseOrderRequest request = new ReviseOrderRequest(List.of(
            new OrderLineItemRequest(1L, "Pizza", new Money("20.00"), 1)
        ));
        doThrow(new IllegalStateException(
            "Cannot modify order in state REJECTION_PENDING. Operation in progress"
        )).when(orderService).reviseOrder(eq(1L), anyList());

        mockMvc.perform(post("/orders/{orderId}/revise", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.errorCode").value("CONFLICT"));
    }

    private CreateOrderRequest createValidOrderRequest(Long menuVersion) {
        return new CreateOrderRequest(
            123L,
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
            123L,
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
}
