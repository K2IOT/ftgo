package net.ftgo.order.domain;

import net.ftgo.common.Address;
import net.ftgo.common.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Order aggregate.
 * 
 * Tests state machine transitions, semantic lock validation, and optimistic locking behavior.
 * 
 * Requirements: 1, 2, 3, 12
 */
class OrderTest {
    
    // ========== Constructor and Validation Tests ==========
    
    @Test
    void testCreateOrder() {
        Long consumerId = 123L;
        Long restaurantId = 456L;
        List<OrderLineItem> lineItems = createSampleLineItems();
        DeliveryInfo deliveryInfo = createSampleDeliveryInfo();
        PaymentInfo paymentInfo = createSamplePaymentInfo();
        
        Order order = new Order(consumerId, restaurantId, lineItems, deliveryInfo, paymentInfo);
        
        assertEquals(consumerId, order.getConsumerId());
        assertEquals(restaurantId, order.getRestaurantId());
        assertEquals(OrderState.APPROVAL_PENDING, order.getState());
        assertEquals(2, order.getLineItems().size());
        assertEquals(deliveryInfo, order.getDeliveryInfo());
        assertEquals(paymentInfo, order.getPaymentInfo());
        assertNotNull(order.getOrderTotal());
        assertNotNull(order.getCreatedAt());
        assertNotNull(order.getUpdatedAt());
        
        // Verify order total is calculated correctly
        Money expectedTotal = new Money(new BigDecimal("65.00")); // (10*2) + (15*3) = 20 + 45 = 65
        assertEquals(expectedTotal, order.getOrderTotal());
    }
    
    @Test
    void testCreateOrderWithNullConsumerId() {
        assertThrows(IllegalArgumentException.class, () -> {
            new Order(null, 456L, createSampleLineItems(), 
                createSampleDeliveryInfo(), createSamplePaymentInfo());
        });
    }
    
    @Test
    void testCreateOrderWithNullRestaurantId() {
        assertThrows(IllegalArgumentException.class, () -> {
            new Order(123L, null, createSampleLineItems(), 
                createSampleDeliveryInfo(), createSamplePaymentInfo());
        });
    }
    
    @Test
    void testCreateOrderWithNullLineItems() {
        assertThrows(IllegalArgumentException.class, () -> {
            new Order(123L, 456L, null, 
                createSampleDeliveryInfo(), createSamplePaymentInfo());
        });
    }
    
    @Test
    void testCreateOrderWithEmptyLineItems() {
        assertThrows(IllegalArgumentException.class, () -> {
            new Order(123L, 456L, Collections.emptyList(), 
                createSampleDeliveryInfo(), createSamplePaymentInfo());
        });
    }
    
    @Test
    void testCreateOrderWithNullDeliveryInfo() {
        assertThrows(IllegalArgumentException.class, () -> {
            new Order(123L, 456L, createSampleLineItems(), 
                null, createSamplePaymentInfo());
        });
    }
    
    @Test
    void testCreateOrderWithNullPaymentInfo() {
        assertThrows(IllegalArgumentException.class, () -> {
            new Order(123L, 456L, createSampleLineItems(), 
                createSampleDeliveryInfo(), null);
        });
    }
    
    @Test
    void testOrderTotalCalculation() {
        List<OrderLineItem> lineItems = Arrays.asList(
            new OrderLineItem(1L, "Burger", new Money(new BigDecimal("12.50")), 2),
            new OrderLineItem(2L, "Fries", new Money(new BigDecimal("4.00")), 3),
            new OrderLineItem(3L, "Soda", new Money(new BigDecimal("2.50")), 4)
        );
        
        Order order = new Order(123L, 456L, lineItems, 
            createSampleDeliveryInfo(), createSamplePaymentInfo());
        
        // (12.50 * 2) + (4.00 * 3) + (2.50 * 4) = 25.00 + 12.00 + 10.00 = 47.00
        Money expectedTotal = new Money(new BigDecimal("47.00"));
        assertEquals(expectedTotal, order.getOrderTotal());
    }
    
    // ========== State Machine Transition Tests - Approval Flow ==========
    
    @Test
    void testApproveOrder() {
        Order order = createSampleOrder();
        assertEquals(OrderState.APPROVAL_PENDING, order.getState());
        
        order.approve();
        
        assertEquals(OrderState.APPROVED, order.getState());
    }
    
    @Test
    void testApproveOrderFromInvalidState() {
        Order order = createSampleOrder();
        order.approve(); // Move to APPROVED
        
        // Cannot approve an already approved order
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            order.approve();
        });
        
        assertTrue(exception.getMessage().contains("Cannot approve order in state APPROVED"));
        assertTrue(exception.getMessage().contains("Expected APPROVAL_PENDING"));
    }
    
    @Test
    void testRejectOrder() {
        Order order = createSampleOrder();
        assertEquals(OrderState.APPROVAL_PENDING, order.getState());
        
        order.reject();
        
        assertEquals(OrderState.REJECTED, order.getState());
    }
    
    @Test
    void testRejectOrderFromInvalidState() {
        Order order = createSampleOrder();
        order.approve(); // Move to APPROVED
        
        // Cannot reject an approved order
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            order.reject();
        });
        
        assertTrue(exception.getMessage().contains("Cannot reject order in state APPROVED"));
        assertTrue(exception.getMessage().contains("Expected APPROVAL_PENDING"));
    }
    
    // ========== State Machine Transition Tests - Cancellation Flow ==========
    
    @Test
    void testBeginCancel() {
        Order order = createSampleOrder();
        order.approve(); // Move to APPROVED
        
        order.beginCancel();
        
        assertEquals(OrderState.CANCEL_PENDING, order.getState());
    }
    
    @Test
    void testBeginCancelFromInvalidState() {
        Order order = createSampleOrder();
        // Order is in APPROVAL_PENDING
        
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            order.beginCancel();
        });
        
        assertTrue(exception.getMessage().contains("Cannot cancel order in state APPROVAL_PENDING"));
        assertTrue(exception.getMessage().contains("Expected APPROVED"));
    }
    
    @Test
    void testConfirmCancel() {
        Order order = createSampleOrder();
        order.approve();
        order.beginCancel();
        
        order.confirmCancel();
        
        assertEquals(OrderState.CANCELLED, order.getState());
    }
    
    @Test
    void testConfirmCancelFromInvalidState() {
        Order order = createSampleOrder();
        order.approve();
        
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            order.confirmCancel();
        });
        
        assertTrue(exception.getMessage().contains("Cannot confirm cancel order in state APPROVED"));
        assertTrue(exception.getMessage().contains("Expected CANCEL_PENDING"));
    }
    
    @Test
    void testUndoCancel() {
        Order order = createSampleOrder();
        order.approve();
        order.beginCancel();
        
        order.undoCancel();
        
        assertEquals(OrderState.APPROVED, order.getState());
    }
    
    @Test
    void testUndoCancelFromInvalidState() {
        Order order = createSampleOrder();
        order.approve();
        
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            order.undoCancel();
        });
        
        assertTrue(exception.getMessage().contains("Cannot undo cancel order in state APPROVED"));
        assertTrue(exception.getMessage().contains("Expected CANCEL_PENDING"));
    }
    
    @Test
    void testCompleteCancellationFlow() {
        Order order = createSampleOrder();
        
        // Initial state
        assertEquals(OrderState.APPROVAL_PENDING, order.getState());
        
        // Approve order
        order.approve();
        assertEquals(OrderState.APPROVED, order.getState());
        
        // Begin cancellation
        order.beginCancel();
        assertEquals(OrderState.CANCEL_PENDING, order.getState());
        
        // Confirm cancellation
        order.confirmCancel();
        assertEquals(OrderState.CANCELLED, order.getState());
    }
    
    @Test
    void testCancellationCompensationFlow() {
        Order order = createSampleOrder();
        
        // Approve and begin cancel
        order.approve();
        order.beginCancel();
        assertEquals(OrderState.CANCEL_PENDING, order.getState());
        
        // Compensation: undo cancel
        order.undoCancel();
        assertEquals(OrderState.APPROVED, order.getState());
    }
    
    // ========== State Machine Transition Tests - Revision Flow ==========
    
    @Test
    void testBeginRevise() {
        Order order = createSampleOrder();
        order.approve();
        
        order.beginRevise();
        
        assertEquals(OrderState.REVISION_PENDING, order.getState());
    }
    
    @Test
    void testBeginReviseFromInvalidState() {
        Order order = createSampleOrder();
        // Order is in APPROVAL_PENDING
        
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            order.beginRevise();
        });
        
        assertTrue(exception.getMessage().contains("Cannot revise order in state APPROVAL_PENDING"));
        assertTrue(exception.getMessage().contains("Expected APPROVED"));
    }
    
    @Test
    void testConfirmRevise() {
        Order order = createSampleOrder();
        order.approve();
        order.beginRevise();
        
        Money originalTotal = order.getOrderTotal();
        
        // Create revised line items with different quantities
        List<OrderLineItem> revisedLineItems = Arrays.asList(
            new OrderLineItem(1L, "Burger", new Money(new BigDecimal("10.00")), 3), // Changed quantity
            new OrderLineItem(2L, "Fries", new Money(new BigDecimal("15.00")), 1)   // Changed quantity
        );
        
        order.confirmRevise(revisedLineItems);
        
        assertEquals(OrderState.APPROVED, order.getState());
        assertEquals(2, order.getLineItems().size());
        
        // Verify order total is recalculated
        Money expectedNewTotal = new Money(new BigDecimal("45.00")); // (10*3) + (15*1) = 30 + 15 = 45
        assertEquals(expectedNewTotal, order.getOrderTotal());
        assertNotEquals(originalTotal, order.getOrderTotal());
    }
    
    @Test
    void testConfirmReviseFromInvalidState() {
        Order order = createSampleOrder();
        order.approve();
        
        List<OrderLineItem> revisedLineItems = createSampleLineItems();
        
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            order.confirmRevise(revisedLineItems);
        });
        
        assertTrue(exception.getMessage().contains("Cannot confirm revise order in state APPROVED"));
        assertTrue(exception.getMessage().contains("Expected REVISION_PENDING"));
    }
    
    @Test
    void testConfirmReviseWithNullLineItems() {
        Order order = createSampleOrder();
        order.approve();
        order.beginRevise();
        
        assertThrows(IllegalArgumentException.class, () -> {
            order.confirmRevise(null);
        });
    }
    
    @Test
    void testConfirmReviseWithEmptyLineItems() {
        Order order = createSampleOrder();
        order.approve();
        order.beginRevise();
        
        assertThrows(IllegalArgumentException.class, () -> {
            order.confirmRevise(Collections.emptyList());
        });
    }
    
    @Test
    void testUndoRevise() {
        Order order = createSampleOrder();
        order.approve();
        order.beginRevise();
        
        order.undoRevise();
        
        assertEquals(OrderState.APPROVED, order.getState());
    }
    
    @Test
    void testUndoReviseFromInvalidState() {
        Order order = createSampleOrder();
        order.approve();
        
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            order.undoRevise();
        });
        
        assertTrue(exception.getMessage().contains("Cannot undo revise order in state APPROVED"));
        assertTrue(exception.getMessage().contains("Expected REVISION_PENDING"));
    }
    
    @Test
    void testCompleteRevisionFlow() {
        Order order = createSampleOrder();
        
        // Approve order
        order.approve();
        assertEquals(OrderState.APPROVED, order.getState());
        
        // Begin revision
        order.beginRevise();
        assertEquals(OrderState.REVISION_PENDING, order.getState());
        
        // Confirm revision
        List<OrderLineItem> revisedLineItems = Arrays.asList(
            new OrderLineItem(1L, "Pizza", new Money(new BigDecimal("20.00")), 1)
        );
        order.confirmRevise(revisedLineItems);
        assertEquals(OrderState.APPROVED, order.getState());
        assertEquals(new Money(new BigDecimal("20.00")), order.getOrderTotal());
    }
    
    @Test
    void testRevisionCompensationFlow() {
        Order order = createSampleOrder();
        Money originalTotal = order.getOrderTotal();
        
        // Approve and begin revise
        order.approve();
        order.beginRevise();
        assertEquals(OrderState.REVISION_PENDING, order.getState());
        
        // Compensation: undo revise
        order.undoRevise();
        assertEquals(OrderState.APPROVED, order.getState());
        
        // Order total should remain unchanged
        assertEquals(originalTotal, order.getOrderTotal());
    }
    
    // ========== Semantic Lock Tests ==========
    
    @Test
    void testIsPending_ApprovalPending() {
        Order order = createSampleOrder();
        assertTrue(order.isPending());
    }
    
    @Test
    void testIsPending_Approved() {
        Order order = createSampleOrder();
        order.approve();
        assertFalse(order.isPending());
    }
    
    @Test
    void testIsPending_Rejected() {
        Order order = createSampleOrder();
        order.reject();
        assertFalse(order.isPending());
    }
    
    @Test
    void testIsPending_CancelPending() {
        Order order = createSampleOrder();
        order.approve();
        order.beginCancel();
        assertTrue(order.isPending());
    }
    
    @Test
    void testIsPending_Cancelled() {
        Order order = createSampleOrder();
        order.approve();
        order.beginCancel();
        order.confirmCancel();
        assertFalse(order.isPending());
    }
    
    @Test
    void testIsPending_RevisionPending() {
        Order order = createSampleOrder();
        order.approve();
        order.beginRevise();
        assertTrue(order.isPending());
    }
    
    @Test
    void testValidateNotPending_ThrowsWhenApprovalPending() {
        Order order = createSampleOrder();
        
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            order.validateNotPending();
        });
        
        assertTrue(exception.getMessage().contains("Cannot modify order in state APPROVAL_PENDING"));
        assertTrue(exception.getMessage().contains("Operation in progress"));
    }
    
    @Test
    void testValidateNotPending_ThrowsWhenCancelPending() {
        Order order = createSampleOrder();
        order.approve();
        order.beginCancel();
        
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            order.validateNotPending();
        });
        
        assertTrue(exception.getMessage().contains("Cannot modify order in state CANCEL_PENDING"));
        assertTrue(exception.getMessage().contains("Operation in progress"));
    }
    
    @Test
    void testValidateNotPending_ThrowsWhenRevisionPending() {
        Order order = createSampleOrder();
        order.approve();
        order.beginRevise();
        
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            order.validateNotPending();
        });
        
        assertTrue(exception.getMessage().contains("Cannot modify order in state REVISION_PENDING"));
        assertTrue(exception.getMessage().contains("Operation in progress"));
    }
    
    @Test
    void testValidateNotPending_SucceedsWhenApproved() {
        Order order = createSampleOrder();
        order.approve();
        
        assertDoesNotThrow(() -> {
            order.validateNotPending();
        });
    }
    
    @Test
    void testValidateNotPending_SucceedsWhenRejected() {
        Order order = createSampleOrder();
        order.reject();
        
        assertDoesNotThrow(() -> {
            order.validateNotPending();
        });
    }
    
    @Test
    void testValidateNotPending_SucceedsWhenCancelled() {
        Order order = createSampleOrder();
        order.approve();
        order.beginCancel();
        order.confirmCancel();
        
        assertDoesNotThrow(() -> {
            order.validateNotPending();
        });
    }
    
    @Test
    void testSemanticLock_PreventsConcurrentCancelDuringApproval() {
        Order order = createSampleOrder();
        // Order is in APPROVAL_PENDING
        
        // Attempting to cancel during approval should fail
        assertThrows(IllegalStateException.class, () -> {
            order.beginCancel();
        });
    }
    
    @Test
    void testSemanticLock_PreventsConcurrentReviseDuringApproval() {
        Order order = createSampleOrder();
        // Order is in APPROVAL_PENDING
        
        // Attempting to revise during approval should fail
        assertThrows(IllegalStateException.class, () -> {
            order.beginRevise();
        });
    }
    
    @Test
    void testSemanticLock_PreventsConcurrentReviseDuringCancellation() {
        Order order = createSampleOrder();
        order.approve();
        order.beginCancel();
        
        // Attempting to revise during cancellation should fail
        assertThrows(IllegalStateException.class, () -> {
            order.beginRevise();
        });
    }
    
    @Test
    void testSemanticLock_PreventsConcurrentCancelDuringRevision() {
        Order order = createSampleOrder();
        order.approve();
        order.beginRevise();
        
        // Attempting to cancel during revision should fail
        assertThrows(IllegalStateException.class, () -> {
            order.beginCancel();
        });
    }
    
    // ========== Optimistic Locking Tests ==========
    
    @Test
    void testVersionFieldInitialization() {
        Order order = createSampleOrder();
        
        // Version should be null before persistence (JPA will set it to 0 on persist)
        assertNull(order.getVersion());
    }
    
    @Test
    void testVersionFieldAfterStateChange() {
        Order order = createSampleOrder();
        
        // Simulate JPA setting version after persist
        setOrderVersion(order, 0);
        assertEquals(0, order.getVersion());
        
        // State changes don't directly modify version (JPA handles this)
        order.approve();
        
        // In a real scenario, JPA would increment version on update
        // We simulate this behavior
        setOrderVersion(order, 1);
        assertEquals(1, order.getVersion());
    }
    
    @Test
    void testVersionFieldIncrementOnMultipleUpdates() {
        Order order = createSampleOrder();
        setOrderVersion(order, 0);
        
        // Simulate multiple state transitions with version increments
        order.approve();
        setOrderVersion(order, 1);
        assertEquals(1, order.getVersion());
        
        order.beginRevise();
        setOrderVersion(order, 2);
        assertEquals(2, order.getVersion());
        
        List<OrderLineItem> revisedLineItems = Arrays.asList(
            new OrderLineItem(1L, "Pizza", new Money(new BigDecimal("20.00")), 1)
        );
        order.confirmRevise(revisedLineItems);
        setOrderVersion(order, 3);
        assertEquals(3, order.getVersion());
    }
    
    // ========== Complex Scenario Tests ==========
    
    @Test
    void testMultipleRevisions() {
        Order order = createSampleOrder();
        order.approve();
        
        Money originalTotal = order.getOrderTotal();
        
        // First revision
        order.beginRevise();
        List<OrderLineItem> revision1 = Arrays.asList(
            new OrderLineItem(1L, "Pizza", new Money(new BigDecimal("20.00")), 2)
        );
        order.confirmRevise(revision1);
        assertEquals(new Money(new BigDecimal("40.00")), order.getOrderTotal());
        
        // Second revision
        order.beginRevise();
        List<OrderLineItem> revision2 = Arrays.asList(
            new OrderLineItem(1L, "Pasta", new Money(new BigDecimal("15.00")), 3)
        );
        order.confirmRevise(revision2);
        assertEquals(new Money(new BigDecimal("45.00")), order.getOrderTotal());
        
        // Verify final state
        assertEquals(OrderState.APPROVED, order.getState());
        assertNotEquals(originalTotal, order.getOrderTotal());
    }
    
    @Test
    void testGetLineItemsReturnsUnmodifiableList() {
        Order order = createSampleOrder();
        List<OrderLineItem> lineItems = order.getLineItems();
        
        // Verify returned list is unmodifiable
        assertThrows(UnsupportedOperationException.class, () -> {
            lineItems.add(new OrderLineItem(99L, "Hacked Item", 
                new Money(new BigDecimal("0.01")), 1));
        });
    }
    
    @Test
    void testOrderToString() {
        Order order = createSampleOrder();
        setOrderId(order, 1L);
        order.approve();
        
        String toString = order.toString();
        
        assertTrue(toString.contains("Order{"));
        assertTrue(toString.contains("id=1"));
        assertTrue(toString.contains("state=APPROVED"));
        assertTrue(toString.contains("consumerId=123"));
        assertTrue(toString.contains("restaurantId=456"));
    }
    
    // ========== Helper Methods ==========
    
    /**
     * Creates a sample Order for testing.
     */
    private Order createSampleOrder() {
        return new Order(
            123L,
            456L,
            createSampleLineItems(),
            createSampleDeliveryInfo(),
            createSamplePaymentInfo()
        );
    }
    
    /**
     * Creates sample line items for testing.
     */
    private List<OrderLineItem> createSampleLineItems() {
        return Arrays.asList(
            new OrderLineItem(1L, "Burger", new Money(new BigDecimal("10.00")), 2),
            new OrderLineItem(2L, "Fries", new Money(new BigDecimal("15.00")), 3)
        );
    }
    
    /**
     * Creates sample delivery info for testing.
     */
    private DeliveryInfo createSampleDeliveryInfo() {
        return new DeliveryInfo(
            "123 Main St, San Francisco, CA 94102",
            LocalDateTime.now().plusHours(1)
        );
    }
    
    /**
     * Creates sample payment info for testing.
     */
    private PaymentInfo createSamplePaymentInfo() {
        return new PaymentInfo("tok_visa_4242");
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
    
    /**
     * Helper method to set order version using reflection (simulates JPA behavior).
     */
    private void setOrderVersion(Order order, Integer version) {
        try {
            var versionField = Order.class.getDeclaredField("version");
            versionField.setAccessible(true);
            versionField.set(order, version);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set order version", e);
        }
    }
}
