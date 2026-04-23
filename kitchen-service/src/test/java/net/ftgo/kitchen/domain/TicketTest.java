package net.ftgo.kitchen.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Ticket aggregate state machine transitions.
 * 
 * Tests:
 * - Valid state transitions
 * - Invalid state transitions (should throw IllegalStateException)
 * - Validation logic
 * - Cancellation and revision workflows
 */
@DisplayName("Ticket State Machine Tests")
class TicketTest {
    
    private static final Long RESTAURANT_ID = 1L;
    private static final Long ORDER_ID = 100L;
    
    private List<TicketLineItem> createSampleLineItems() {
        return Arrays.asList(
            new TicketLineItem(1L, "Burger", 2),
            new TicketLineItem(2L, "Fries", 1)
        );
    }
    
    @Test
    @DisplayName("Should create ticket in CREATE_PENDING state")
    void shouldCreateTicketInCreatePendingState() {
        // Given
        List<TicketLineItem> lineItems = createSampleLineItems();
        
        // When
        Ticket ticket = new Ticket(RESTAURANT_ID, ORDER_ID, lineItems);
        
        // Then
        assertNotNull(ticket);
        assertEquals(TicketState.CREATE_PENDING, ticket.getState());
        assertEquals(RESTAURANT_ID, ticket.getRestaurantId());
        assertEquals(ORDER_ID, ticket.getOrderId());
        assertEquals(2, ticket.getLineItems().size());
        assertNotNull(ticket.getCreatedAt());
    }
    
    @Test
    @DisplayName("Should reject null restaurant ID")
    void shouldRejectNullRestaurantId() {
        // Given
        List<TicketLineItem> lineItems = createSampleLineItems();
        
        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new Ticket(null, ORDER_ID, lineItems)
        );
        assertEquals("Restaurant ID cannot be null", exception.getMessage());
    }
    
    @Test
    @DisplayName("Should reject null order ID")
    void shouldRejectNullOrderId() {
        // Given
        List<TicketLineItem> lineItems = createSampleLineItems();
        
        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new Ticket(RESTAURANT_ID, null, lineItems)
        );
        assertEquals("Order ID cannot be null", exception.getMessage());
    }
    
    @Test
    @DisplayName("Should reject null line items")
    void shouldRejectNullLineItems() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new Ticket(RESTAURANT_ID, ORDER_ID, null)
        );
        assertEquals("Line items cannot be null or empty", exception.getMessage());
    }
    
    @Test
    @DisplayName("Should reject empty line items")
    void shouldRejectEmptyLineItems() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new Ticket(RESTAURANT_ID, ORDER_ID, Collections.emptyList())
        );
        assertEquals("Line items cannot be null or empty", exception.getMessage());
    }
    
    @Test
    @DisplayName("Should transition from CREATE_PENDING to AWAITING_ACCEPTANCE on approve")
    void shouldTransitionToAwaitingAcceptanceOnApprove() {
        // Given
        Ticket ticket = new Ticket(RESTAURANT_ID, ORDER_ID, createSampleLineItems());
        assertEquals(TicketState.CREATE_PENDING, ticket.getState());
        
        // When
        ticket.approve();
        
        // Then
        assertEquals(TicketState.AWAITING_ACCEPTANCE, ticket.getState());
    }
    
    @Test
    @DisplayName("Should reject approve when not in CREATE_PENDING state")
    void shouldRejectApproveWhenNotInCreatePendingState() {
        // Given
        Ticket ticket = new Ticket(RESTAURANT_ID, ORDER_ID, createSampleLineItems());
        ticket.approve(); // Now in AWAITING_ACCEPTANCE
        
        // When & Then
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            ticket::approve
        );
        assertTrue(exception.getMessage().contains("Cannot approve ticket in state AWAITING_ACCEPTANCE"));
    }
    
    @Test
    @DisplayName("Should transition from AWAITING_ACCEPTANCE to ACCEPTED on accept")
    void shouldTransitionToAcceptedOnAccept() {
        // Given
        Ticket ticket = new Ticket(RESTAURANT_ID, ORDER_ID, createSampleLineItems());
        ticket.approve();
        assertEquals(TicketState.AWAITING_ACCEPTANCE, ticket.getState());
        assertNull(ticket.getAcceptedAt());
        
        // When
        ticket.accept();
        
        // Then
        assertEquals(TicketState.ACCEPTED, ticket.getState());
        assertNotNull(ticket.getAcceptedAt());
    }
    
    @Test
    @DisplayName("Should reject accept when not in AWAITING_ACCEPTANCE state")
    void shouldRejectAcceptWhenNotInAwaitingAcceptanceState() {
        // Given
        Ticket ticket = new Ticket(RESTAURANT_ID, ORDER_ID, createSampleLineItems());
        assertEquals(TicketState.CREATE_PENDING, ticket.getState());
        
        // When & Then
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            ticket::accept
        );
        assertTrue(exception.getMessage().contains("Cannot accept ticket in state CREATE_PENDING"));
    }
    
    @Test
    @DisplayName("Should transition from ACCEPTED to PREPARING on preparing")
    void shouldTransitionToPreparingOnPreparing() {
        // Given
        Ticket ticket = new Ticket(RESTAURANT_ID, ORDER_ID, createSampleLineItems());
        ticket.approve();
        ticket.accept();
        assertEquals(TicketState.ACCEPTED, ticket.getState());
        
        // When
        ticket.preparing();
        
        // Then
        assertEquals(TicketState.PREPARING, ticket.getState());
    }
    
    @Test
    @DisplayName("Should reject preparing when not in ACCEPTED state")
    void shouldRejectPreparingWhenNotInAcceptedState() {
        // Given
        Ticket ticket = new Ticket(RESTAURANT_ID, ORDER_ID, createSampleLineItems());
        ticket.approve();
        assertEquals(TicketState.AWAITING_ACCEPTANCE, ticket.getState());
        
        // When & Then
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            ticket::preparing
        );
        assertTrue(exception.getMessage().contains("Cannot mark ticket as preparing in state AWAITING_ACCEPTANCE"));
    }
    
    @Test
    @DisplayName("Should transition from PREPARING to READY_FOR_PICKUP on readyForPickup")
    void shouldTransitionToReadyForPickupOnReadyForPickup() {
        // Given
        Ticket ticket = new Ticket(RESTAURANT_ID, ORDER_ID, createSampleLineItems());
        ticket.approve();
        ticket.accept();
        ticket.preparing();
        assertEquals(TicketState.PREPARING, ticket.getState());
        assertNull(ticket.getPreparedAt());
        assertNull(ticket.getReadyBy());
        
        // When
        ticket.readyForPickup();
        
        // Then
        assertEquals(TicketState.READY_FOR_PICKUP, ticket.getState());
        assertNotNull(ticket.getPreparedAt());
        assertNotNull(ticket.getReadyBy());
    }
    
    @Test
    @DisplayName("Should reject readyForPickup when not in PREPARING state")
    void shouldRejectReadyForPickupWhenNotInPreparingState() {
        // Given
        Ticket ticket = new Ticket(RESTAURANT_ID, ORDER_ID, createSampleLineItems());
        ticket.approve();
        ticket.accept();
        assertEquals(TicketState.ACCEPTED, ticket.getState());
        
        // When & Then
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            ticket::readyForPickup
        );
        assertTrue(exception.getMessage().contains("Cannot mark ticket as ready in state ACCEPTED"));
    }
    
    @Test
    @DisplayName("Should transition from READY_FOR_PICKUP to PICKED_UP on pickedUp")
    void shouldTransitionToPickedUpOnPickedUp() {
        // Given
        Ticket ticket = new Ticket(RESTAURANT_ID, ORDER_ID, createSampleLineItems());
        ticket.approve();
        ticket.accept();
        ticket.preparing();
        ticket.readyForPickup();
        assertEquals(TicketState.READY_FOR_PICKUP, ticket.getState());
        
        // When
        ticket.pickedUp();
        
        // Then
        assertEquals(TicketState.PICKED_UP, ticket.getState());
    }
    
    @Test
    @DisplayName("Should reject pickedUp when not in READY_FOR_PICKUP state")
    void shouldRejectPickedUpWhenNotInReadyForPickupState() {
        // Given
        Ticket ticket = new Ticket(RESTAURANT_ID, ORDER_ID, createSampleLineItems());
        ticket.approve();
        ticket.accept();
        ticket.preparing();
        assertEquals(TicketState.PREPARING, ticket.getState());
        
        // When & Then
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            ticket::pickedUp
        );
        assertTrue(exception.getMessage().contains("Cannot mark ticket as picked up in state PREPARING"));
    }
    
    @Test
    @DisplayName("Should cancel ticket from any state")
    void shouldCancelTicketFromAnyState() {
        // Test cancellation from CREATE_PENDING
        Ticket ticket1 = new Ticket(RESTAURANT_ID, ORDER_ID, createSampleLineItems());
        ticket1.cancel();
        assertEquals(TicketState.CANCELLED, ticket1.getState());
        
        // Test cancellation from AWAITING_ACCEPTANCE
        Ticket ticket2 = new Ticket(RESTAURANT_ID, ORDER_ID, createSampleLineItems());
        ticket2.approve();
        ticket2.cancel();
        assertEquals(TicketState.CANCELLED, ticket2.getState());
        
        // Test cancellation from ACCEPTED
        Ticket ticket3 = new Ticket(RESTAURANT_ID, ORDER_ID, createSampleLineItems());
        ticket3.approve();
        ticket3.accept();
        ticket3.cancel();
        assertEquals(TicketState.CANCELLED, ticket3.getState());
    }
    
    @Test
    @DisplayName("Should handle beginCancel workflow")
    void shouldHandleBeginCancelWorkflow() {
        // Given
        Ticket ticket = new Ticket(RESTAURANT_ID, ORDER_ID, createSampleLineItems());
        ticket.approve();
        
        // When
        assertDoesNotThrow(ticket::beginCancel);
        
        // Then - state should not change yet
        assertEquals(TicketState.AWAITING_ACCEPTANCE, ticket.getState());
    }
    
    @Test
    @DisplayName("Should reject beginCancel when already cancelled")
    void shouldRejectBeginCancelWhenAlreadyCancelled() {
        // Given
        Ticket ticket = new Ticket(RESTAURANT_ID, ORDER_ID, createSampleLineItems());
        ticket.cancel();
        
        // When & Then
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            ticket::beginCancel
        );
        assertEquals("Ticket is already cancelled", exception.getMessage());
    }
    
    @Test
    @DisplayName("Should confirm cancellation")
    void shouldConfirmCancellation() {
        // Given
        Ticket ticket = new Ticket(RESTAURANT_ID, ORDER_ID, createSampleLineItems());
        ticket.approve();
        ticket.beginCancel();
        
        // When
        ticket.confirmCancel();
        
        // Then
        assertEquals(TicketState.CANCELLED, ticket.getState());
    }
    
    @Test
    @DisplayName("Should undo cancellation")
    void shouldUndoCancellation() {
        // Given
        Ticket ticket = new Ticket(RESTAURANT_ID, ORDER_ID, createSampleLineItems());
        ticket.approve();
        ticket.cancel();
        assertEquals(TicketState.CANCELLED, ticket.getState());
        
        // When
        ticket.undoCancel();
        
        // Then
        assertEquals(TicketState.AWAITING_ACCEPTANCE, ticket.getState());
    }
    
    @Test
    @DisplayName("Should handle beginRevise workflow")
    void shouldHandleBeginReviseWorkflow() {
        // Given
        Ticket ticket = new Ticket(RESTAURANT_ID, ORDER_ID, createSampleLineItems());
        ticket.approve();
        List<TicketLineItem> revisedItems = Arrays.asList(
            new TicketLineItem(1L, "Burger", 3),
            new TicketLineItem(3L, "Salad", 1)
        );
        
        // When
        assertDoesNotThrow(() -> ticket.beginRevise(revisedItems));
        
        // Then - line items should not change yet
        assertEquals(2, ticket.getLineItems().size());
    }
    
    @Test
    @DisplayName("Should reject beginRevise when cancelled")
    void shouldRejectBeginReviseWhenCancelled() {
        // Given
        Ticket ticket = new Ticket(RESTAURANT_ID, ORDER_ID, createSampleLineItems());
        ticket.cancel();
        List<TicketLineItem> revisedItems = Arrays.asList(
            new TicketLineItem(1L, "Burger", 3)
        );
        
        // When & Then
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> ticket.beginRevise(revisedItems)
        );
        assertTrue(exception.getMessage().contains("Cannot revise ticket in state CANCELLED"));
    }
    
    @Test
    @DisplayName("Should reject beginRevise when picked up")
    void shouldRejectBeginReviseWhenPickedUp() {
        // Given
        Ticket ticket = new Ticket(RESTAURANT_ID, ORDER_ID, createSampleLineItems());
        ticket.approve();
        ticket.accept();
        ticket.preparing();
        ticket.readyForPickup();
        ticket.pickedUp();
        List<TicketLineItem> revisedItems = Arrays.asList(
            new TicketLineItem(1L, "Burger", 3)
        );
        
        // When & Then
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> ticket.beginRevise(revisedItems)
        );
        assertTrue(exception.getMessage().contains("Cannot revise ticket in state PICKED_UP"));
    }
    
    @Test
    @DisplayName("Should confirm revision and update line items")
    void shouldConfirmRevisionAndUpdateLineItems() {
        // Given
        Ticket ticket = new Ticket(RESTAURANT_ID, ORDER_ID, createSampleLineItems());
        ticket.approve();
        List<TicketLineItem> revisedItems = Arrays.asList(
            new TicketLineItem(1L, "Burger", 3),
            new TicketLineItem(3L, "Salad", 1)
        );
        ticket.beginRevise(revisedItems);
        
        // When
        ticket.confirmRevise(revisedItems);
        
        // Then
        assertEquals(2, ticket.getLineItems().size());
        assertEquals(3, ticket.getLineItems().get(0).getQuantity());
        assertEquals("Salad", ticket.getLineItems().get(1).getName());
    }
    
    @Test
    @DisplayName("Should undo revision and restore original line items")
    void shouldUndoRevisionAndRestoreOriginalLineItems() {
        // Given
        Ticket ticket = new Ticket(RESTAURANT_ID, ORDER_ID, createSampleLineItems());
        ticket.approve();
        List<TicketLineItem> originalItems = ticket.getLineItems();
        List<TicketLineItem> revisedItems = Arrays.asList(
            new TicketLineItem(1L, "Burger", 3)
        );
        ticket.confirmRevise(revisedItems);
        assertEquals(1, ticket.getLineItems().size());
        
        // When
        ticket.undoRevise(Arrays.asList(
            new TicketLineItem(1L, "Burger", 2),
            new TicketLineItem(2L, "Fries", 1)
        ));
        
        // Then
        assertEquals(2, ticket.getLineItems().size());
        assertEquals(2, ticket.getLineItems().get(0).getQuantity());
        assertEquals("Fries", ticket.getLineItems().get(1).getName());
    }
    
    @Test
    @DisplayName("Should complete full happy path workflow")
    void shouldCompleteFullHappyPathWorkflow() {
        // Given
        Ticket ticket = new Ticket(RESTAURANT_ID, ORDER_ID, createSampleLineItems());
        
        // When & Then - Full workflow
        assertEquals(TicketState.CREATE_PENDING, ticket.getState());
        
        ticket.approve();
        assertEquals(TicketState.AWAITING_ACCEPTANCE, ticket.getState());
        
        ticket.accept();
        assertEquals(TicketState.ACCEPTED, ticket.getState());
        assertNotNull(ticket.getAcceptedAt());
        
        ticket.preparing();
        assertEquals(TicketState.PREPARING, ticket.getState());
        
        ticket.readyForPickup();
        assertEquals(TicketState.READY_FOR_PICKUP, ticket.getState());
        assertNotNull(ticket.getPreparedAt());
        assertNotNull(ticket.getReadyBy());
        
        ticket.pickedUp();
        assertEquals(TicketState.PICKED_UP, ticket.getState());
    }
}
