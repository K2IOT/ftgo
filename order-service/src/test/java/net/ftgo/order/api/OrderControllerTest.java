package net.ftgo.order.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.ftgo.common.Address;
import net.ftgo.common.Money;
import net.ftgo.order.domain.*;
import net.ftgo.order.service.OrderNotFoundException;
import net.ftgo.order.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for Order Service REST API using MockMvc.
 * 
 * Tests all REST endpoints with mocked service layer:
 * - POST /orders - Create order and initiate saga
 * - GET /orders/{orderId} - Get order details
 * - POST /orders/{orderId}/cancel - Cancel order and initiate CancelOrderSaga
 * - POST /orders/{orderId}/revise - Revise order and initiate ReviseOrderSaga
 * - Concurrent modification returns 409 Conflict
 * 
 * Requirements: 1, 2, 3, 12
 */
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
        // Create sample order for testing
        sampleOrder = createSampleOrder();
    }
    
    // ========== POST /orders - Create Order Tests ==========
    
    @Test
    void testCreateOrder_Success() throws Exception {
        // Given: Valid create order request
        CreateOrderRequest request = createValidOrderRequest();
        Long expectedOrderId = 1L;
        
        when(orderService.createOrder(
            anyLong(), anyLong(), anyList(), any(DeliveryInfo.class), any(PaymentInfo.class)))
            .thenReturn(expectedOrderId);
        
        // When: POST /orders
        mockMvc.perform(post("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.orderId").value(expectedOrderId));
        
        // Then: Service method was called
        verify(orderService).createOrder(
            eq(123L), eq(456L), anyList(), any(DeliveryInfo.class), any(PaymentInfo.class));
    }
    
    @Test
    void testCreateOrder_InitiatesSaga() throws Exception {
        // Given: Valid create order request
        CreateOrderRequest request = createValidOrderRequest();
        Long expectedOrderId = 1L;
        
        when(orderService.createOrder(anyLong(), anyLong(), anyList(), any(), any()))
            .thenReturn(expectedOrderId);
        
        // When: POST /orders
        mockMvc.perform(post("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.orderId").value(expectedOrderId));
        
        // Then: CreateOrderSaga is initiated via service
        verify(orderService).createOrder(
            eq(123L), eq(456L), anyList(), any(DeliveryInfo.class), any(PaymentInfo.class));
    }
    
    // ========== GET /orders/{orderId} - Get Order Tests ==========
    
    @Test
    void testGetOrder_Success() throws Exception {
        // Given: Existing order
        Long orderId = 1L;
        setOrderId(sampleOrder, orderId);
        
        when(orderService.getOrder(orderId)).thenReturn(sampleOrder);
        
        // When: GET /orders/{orderId}
        mockMvc.perform(get("/orders/{orderId}", orderId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(orderId))
            .andExpect(jsonPath("$.state").value("APPROVAL_PENDING"))
            .andExpect(jsonPath("$.consumerId").value(123))
            .andExpect(jsonPath("$.restaurantId").value(456))
            .andExpect(jsonPath("$.lineItems").isArray())
            .andExpect(jsonPath("$.lineItems.length()").value(2))
            .andExpect(jsonPath("$.orderTotal.amount").value(65.00));
        
        verify(orderService).getOrder(orderId);
    }
    
    @Test
    void testGetOrder_NotFound() throws Exception {
        // Given: Non-existent order ID
        Long nonExistentId = 99999L;
        
        when(orderService.getOrder(nonExistentId))
            .thenThrow(new OrderNotFoundException("Order not found: " + nonExistentId));
        
        // When: GET /orders/{orderId}
        // Then: Returns 404 Not Found
        mockMvc.perform(get("/orders/{orderId}", nonExistentId))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("ORDER_NOT_FOUND"))
            .andExpect(jsonPath("$.message").value("Order not found: " + nonExistentId));
        
        verify(orderService).getOrder(nonExistentId);
    }
    
    @Test
    void testGetOrder_ReturnsCompleteOrderDetails() throws Exception {
        // Given: Order with multiple line items
        Long orderId = 1L;
        setOrderId(sampleOrder, orderId);
        
        when(orderService.getOrder(orderId)).thenReturn(sampleOrder);
        
        // When: GET /orders/{orderId}
        mockMvc.perform(get("/orders/{orderId}", orderId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(orderId))
            .andExpect(jsonPath("$.state").value("APPROVAL_PENDING"))
            .andExpect(jsonPath("$.consumerId").value(123))
            .andExpect(jsonPath("$.restaurantId").value(456))
            .andExpect(jsonPath("$.lineItems").isArray())
            .andExpect(jsonPath("$.lineItems.length()").value(2))
            .andExpect(jsonPath("$.deliveryAddress").isNotEmpty())
            .andExpect(jsonPath("$.deliveryTime").isNotEmpty())
            .andExpect(jsonPath("$.orderTotal.amount").value(65.00))
            .andExpect(jsonPath("$.createdAt").isNotEmpty());
        
        verify(orderService).getOrder(orderId);
    }
    
    // ========== POST /orders/{orderId}/cancel - Cancel Order Tests ==========
    
    @Test
    void testCancelOrder_Success() throws Exception {
        // Given: Order in APPROVED state
        Long orderId = 1L;
        
        doNothing().when(orderService).cancelOrder(orderId);
        
        // When: POST /orders/{orderId}/cancel
        mockMvc.perform(post("/orders/{orderId}/cancel", orderId))
            .andExpect(status().isOk());
        
        // Then: Service method was called
        verify(orderService).cancelOrder(orderId);
    }
    
    @Test
    void testCancelOrder_NotFound() throws Exception {
        // Given: Non-existent order ID
        Long nonExistentId = 99999L;
        
        doThrow(new OrderNotFoundException("Order not found: " + nonExistentId))
            .when(orderService).cancelOrder(nonExistentId);
        
        // When: POST /orders/{orderId}/cancel
        // Then: Returns 404 Not Found
        mockMvc.perform(post("/orders/{orderId}/cancel", nonExistentId))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("ORDER_NOT_FOUND"));
        
        verify(orderService).cancelOrder(nonExistentId);
    }
    
    @Test
    void testCancelOrder_InvalidState_ApprovalPending() throws Exception {
        // Given: Order in APPROVAL_PENDING state
        Long orderId = 1L;
        
        doThrow(new IllegalStateException("Cannot cancel order in state APPROVAL_PENDING. Expected APPROVED"))
            .when(orderService).cancelOrder(orderId);
        
        // When: POST /orders/{orderId}/cancel
        // Then: Returns 409 Conflict (semantic lock violation)
        mockMvc.perform(post("/orders/{orderId}/cancel", orderId))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.errorCode").value("CONFLICT"))
            .andExpect(jsonPath("$.message").value("Cannot cancel order in state APPROVAL_PENDING. Expected APPROVED"));
        
        verify(orderService).cancelOrder(orderId);
    }
    
    @Test
    void testCancelOrder_InitiatesCancelOrderSaga() throws Exception {
        // Given: Order in APPROVED state
        Long orderId = 1L;
        
        doNothing().when(orderService).cancelOrder(orderId);
        
        // When: POST /orders/{orderId}/cancel
        mockMvc.perform(post("/orders/{orderId}/cancel", orderId))
            .andExpect(status().isOk());
        
        // Then: CancelOrderSaga is initiated via service
        verify(orderService).cancelOrder(orderId);
    }
    
    // ========== POST /orders/{orderId}/revise - Revise Order Tests ==========
    
    @Test
    void testReviseOrder_Success() throws Exception {
        // Given: Order in APPROVED state
        Long orderId = 1L;
        
        ReviseOrderRequest request = new ReviseOrderRequest(
            Arrays.asList(
                new OrderLineItemRequest(1L, "Pizza", new Money(new BigDecimal("20.00")), 2),
                new OrderLineItemRequest(2L, "Salad", new Money(new BigDecimal("10.00")), 1)
            )
        );
        
        doNothing().when(orderService).reviseOrder(eq(orderId), anyList());
        
        // When: POST /orders/{orderId}/revise
        mockMvc.perform(post("/orders/{orderId}/revise", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk());
        
        // Then: Service method was called
        verify(orderService).reviseOrder(eq(orderId), anyList());
    }
    
    @Test
    void testReviseOrder_NotFound() throws Exception {
        // Given: Non-existent order ID
        Long nonExistentId = 99999L;
        ReviseOrderRequest request = new ReviseOrderRequest(
            Arrays.asList(
                new OrderLineItemRequest(1L, "Pizza", new Money(new BigDecimal("20.00")), 1)
            )
        );
        
        doThrow(new OrderNotFoundException("Order not found: " + nonExistentId))
            .when(orderService).reviseOrder(eq(nonExistentId), anyList());
        
        // When: POST /orders/{orderId}/revise
        // Then: Returns 404 Not Found
        mockMvc.perform(post("/orders/{orderId}/revise", nonExistentId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("ORDER_NOT_FOUND"));
        
        verify(orderService).reviseOrder(eq(nonExistentId), anyList());
    }
    
    @Test
    void testReviseOrder_InvalidState_ApprovalPending() throws Exception {
        // Given: Order in APPROVAL_PENDING state
        Long orderId = 1L;
        
        ReviseOrderRequest request = new ReviseOrderRequest(
            Arrays.asList(
                new OrderLineItemRequest(1L, "Pizza", new Money(new BigDecimal("20.00")), 1)
            )
        );
        
        doThrow(new IllegalStateException("Cannot revise order in state APPROVAL_PENDING. Expected APPROVED"))
            .when(orderService).reviseOrder(eq(orderId), anyList());
        
        // When: POST /orders/{orderId}/revise
        // Then: Returns 409 Conflict (semantic lock violation)
        mockMvc.perform(post("/orders/{orderId}/revise", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.errorCode").value("CONFLICT"))
            .andExpect(jsonPath("$.message").value("Cannot revise order in state APPROVAL_PENDING. Expected APPROVED"));
        
        verify(orderService).reviseOrder(eq(orderId), anyList());
    }
    
    @Test
    void testReviseOrder_InitiatesReviseOrderSaga() throws Exception {
        // Given: Order in APPROVED state
        Long orderId = 1L;
        
        ReviseOrderRequest request = new ReviseOrderRequest(
            Arrays.asList(
                new OrderLineItemRequest(1L, "Pizza", new Money(new BigDecimal("20.00")), 2)
            )
        );
        
        doNothing().when(orderService).reviseOrder(eq(orderId), anyList());
        
        // When: POST /orders/{orderId}/revise
        mockMvc.perform(post("/orders/{orderId}/revise", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk());
        
        // Then: ReviseOrderSaga is initiated via service
        verify(orderService).reviseOrder(eq(orderId), anyList());
    }
    
    // ========== Concurrent Modification Tests (Semantic Lock) ==========
    
    @Test
    void testConcurrentModification_CancelDuringApproval() throws Exception {
        // Given: Order in APPROVAL_PENDING state
        Long orderId = 1L;
        
        doThrow(new IllegalStateException("Cannot modify order in state APPROVAL_PENDING. Operation in progress"))
            .when(orderService).cancelOrder(orderId);
        
        // When: Attempt to cancel during approval
        // Then: Returns 409 Conflict
        mockMvc.perform(post("/orders/{orderId}/cancel", orderId))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.errorCode").value("CONFLICT"))
            .andExpect(jsonPath("$.message").value("Cannot modify order in state APPROVAL_PENDING. Operation in progress"));
        
        verify(orderService).cancelOrder(orderId);
    }
    
    @Test
    void testConcurrentModification_ReviseDuringApproval() throws Exception {
        // Given: Order in APPROVAL_PENDING state
        Long orderId = 1L;
        
        ReviseOrderRequest request = new ReviseOrderRequest(
            Arrays.asList(
                new OrderLineItemRequest(1L, "Pizza", new Money(new BigDecimal("20.00")), 1)
            )
        );
        
        doThrow(new IllegalStateException("Cannot modify order in state APPROVAL_PENDING. Operation in progress"))
            .when(orderService).reviseOrder(eq(orderId), anyList());
        
        // When: Attempt to revise during approval
        // Then: Returns 409 Conflict
        mockMvc.perform(post("/orders/{orderId}/revise", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.errorCode").value("CONFLICT"))
            .andExpect(jsonPath("$.message").value("Cannot modify order in state APPROVAL_PENDING. Operation in progress"));
        
        verify(orderService).reviseOrder(eq(orderId), anyList());
    }
    
    @Test
    void testConcurrentModification_ReviseDuringCancellation() throws Exception {
        // Given: Order in CANCEL_PENDING state
        Long orderId = 1L;
        
        ReviseOrderRequest request = new ReviseOrderRequest(
            Arrays.asList(
                new OrderLineItemRequest(1L, "Pizza", new Money(new BigDecimal("20.00")), 1)
            )
        );
        
        doThrow(new IllegalStateException("Cannot modify order in state CANCEL_PENDING. Operation in progress"))
            .when(orderService).reviseOrder(eq(orderId), anyList());
        
        // When: Attempt to revise during cancellation
        // Then: Returns 409 Conflict
        mockMvc.perform(post("/orders/{orderId}/revise", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.errorCode").value("CONFLICT"))
            .andExpect(jsonPath("$.message").value("Cannot modify order in state CANCEL_PENDING. Operation in progress"));
        
        verify(orderService).reviseOrder(eq(orderId), anyList());
    }
    
    @Test
    void testConcurrentModification_CancelDuringRevision() throws Exception {
        // Given: Order in REVISION_PENDING state
        Long orderId = 1L;
        
        doThrow(new IllegalStateException("Cannot modify order in state REVISION_PENDING. Operation in progress"))
            .when(orderService).cancelOrder(orderId);
        
        // When: Attempt to cancel during revision
        // Then: Returns 409 Conflict
        mockMvc.perform(post("/orders/{orderId}/cancel", orderId))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.errorCode").value("CONFLICT"))
            .andExpect(jsonPath("$.message").value("Cannot modify order in state REVISION_PENDING. Operation in progress"));
        
        verify(orderService).cancelOrder(orderId);
    }
    
    // ========== Helper Methods ==========
    
    /**
     * Creates a sample Address for testing.
     */
    private Address createSampleAddress() {
        return new Address(
            "123 Main St",
            "San Francisco",
            "CA",
            "94102"
        );
    }
    
    /**
     * Creates a valid CreateOrderRequest for testing.
     */
    private CreateOrderRequest createValidOrderRequest() {
        List<OrderLineItemRequest> lineItems = Arrays.asList(
            new OrderLineItemRequest(1L, "Burger", new Money(new BigDecimal("10.00")), 2),
            new OrderLineItemRequest(2L, "Fries", new Money(new BigDecimal("15.00")), 3)
        );
        
        return new CreateOrderRequest(
            123L,
            456L,
            lineItems,
            createSampleAddress(),
            LocalDateTime.now().plusHours(1),
            "tok_visa_4242"
        );
    }
    
    /**
     * Creates a sample Order for testing.
     */
    private Order createSampleOrder() {
        return new Order(
            123L,
            456L,
            Arrays.asList(
                new OrderLineItem(1L, "Burger", new Money(new BigDecimal("10.00")), 2),
                new OrderLineItem(2L, "Fries", new Money(new BigDecimal("15.00")), 3)
            ),
            new DeliveryInfo(
                "123 Main St, San Francisco, CA 94102",
                LocalDateTime.now().plusHours(1)
            ),
            new PaymentInfo("tok_visa_4242")
        );
    }
    
    /**
     * Helper method to set order ID using reflection (simulates JPA behavior).
     */
    private void setOrderId(Order order, Long id) {
        try {
            var idField = Order.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(order, id);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set order ID", e);
        }
    }
}
