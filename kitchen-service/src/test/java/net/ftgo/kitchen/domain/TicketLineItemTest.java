package net.ftgo.kitchen.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TicketLineItem entity.
 * 
 * Tests validation logic for menu item ID, name, and quantity.
 */
@DisplayName("TicketLineItem Validation Tests")
class TicketLineItemTest {
    
    @Test
    @DisplayName("Should create valid ticket line item")
    void shouldCreateValidTicketLineItem() {
        // When
        TicketLineItem item = new TicketLineItem(1L, "Burger", 2);
        
        // Then
        assertNotNull(item);
        assertEquals(1L, item.getMenuItemId());
        assertEquals("Burger", item.getName());
        assertEquals(2, item.getQuantity());
    }
    
    @Test
    @DisplayName("Should reject null menu item ID")
    void shouldRejectNullMenuItemId() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new TicketLineItem(null, "Burger", 2)
        );
        assertEquals("Menu item ID cannot be null", exception.getMessage());
    }
    
    @Test
    @DisplayName("Should reject null name")
    void shouldRejectNullName() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new TicketLineItem(1L, null, 2)
        );
        assertEquals("Name cannot be null or blank", exception.getMessage());
    }
    
    @Test
    @DisplayName("Should reject blank name")
    void shouldRejectBlankName() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new TicketLineItem(1L, "   ", 2)
        );
        assertEquals("Name cannot be null or blank", exception.getMessage());
    }
    
    @Test
    @DisplayName("Should reject empty name")
    void shouldRejectEmptyName() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new TicketLineItem(1L, "", 2)
        );
        assertEquals("Name cannot be null or blank", exception.getMessage());
    }
    
    @Test
    @DisplayName("Should reject null quantity")
    void shouldRejectNullQuantity() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new TicketLineItem(1L, "Burger", null)
        );
        assertEquals("Quantity cannot be null", exception.getMessage());
    }
    
    @Test
    @DisplayName("Should reject zero quantity")
    void shouldRejectZeroQuantity() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new TicketLineItem(1L, "Burger", 0)
        );
        assertEquals("Quantity must be positive", exception.getMessage());
    }
    
    @Test
    @DisplayName("Should reject negative quantity")
    void shouldRejectNegativeQuantity() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new TicketLineItem(1L, "Burger", -1)
        );
        assertEquals("Quantity must be positive", exception.getMessage());
    }
    
    @Test
    @DisplayName("Should accept quantity of 1")
    void shouldAcceptQuantityOfOne() {
        // When
        TicketLineItem item = new TicketLineItem(1L, "Burger", 1);
        
        // Then
        assertEquals(1, item.getQuantity());
    }
    
    @Test
    @DisplayName("Should accept large quantity")
    void shouldAcceptLargeQuantity() {
        // When
        TicketLineItem item = new TicketLineItem(1L, "Burger", 100);
        
        // Then
        assertEquals(100, item.getQuantity());
    }
}
