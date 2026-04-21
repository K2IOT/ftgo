package net.ftgo.restaurant.domain;

import net.ftgo.common.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MenuItem entity.
 */
class MenuItemTest {
    
    @Test
    void testCreateMenuItem() {
        Money price = new Money(new BigDecimal("12.99"));
        MenuItem menuItem = new MenuItem(1L, "Burger", "Delicious beef burger", price);
        
        assertEquals(1L, menuItem.getRestaurantId());
        assertEquals("Burger", menuItem.getName());
        assertEquals("Delicious beef burger", menuItem.getDescription());
        assertEquals(price, menuItem.getPrice());
        assertTrue(menuItem.isAvailable());
        assertNotNull(menuItem.getCreatedAt());
        assertNotNull(menuItem.getUpdatedAt());
    }
    
    @Test
    void testCreateMenuItemWithNullRestaurantId() {
        Money price = new Money(new BigDecimal("12.99"));
        
        assertThrows(IllegalArgumentException.class, () -> {
            new MenuItem(null, "Burger", "Delicious beef burger", price);
        });
    }
    
    @Test
    void testCreateMenuItemWithNullName() {
        Money price = new Money(new BigDecimal("12.99"));
        
        assertThrows(IllegalArgumentException.class, () -> {
            new MenuItem(1L, null, "Delicious beef burger", price);
        });
    }
    
    @Test
    void testCreateMenuItemWithBlankName() {
        Money price = new Money(new BigDecimal("12.99"));
        
        assertThrows(IllegalArgumentException.class, () -> {
            new MenuItem(1L, "   ", "Delicious beef burger", price);
        });
    }
    
    @Test
    void testCreateMenuItemWithNullPrice() {
        assertThrows(IllegalArgumentException.class, () -> {
            new MenuItem(1L, "Burger", "Delicious beef burger", null);
        });
    }
    
    @Test
    void testCreateMenuItemWithZeroPrice() {
        Money zeroPrice = new Money(BigDecimal.ZERO);
        
        assertThrows(IllegalArgumentException.class, () -> {
            new MenuItem(1L, "Burger", "Delicious beef burger", zeroPrice);
        });
    }
    
    @Test
    void testCreateMenuItemWithNegativePrice() {
        assertThrows(IllegalArgumentException.class, () -> {
            new MenuItem(1L, "Burger", "Delicious beef burger", new Money(new BigDecimal("-5.00")));
        });
    }
    
    @Test
    void testUpdatePrice() {
        Money initialPrice = new Money(new BigDecimal("12.99"));
        MenuItem menuItem = new MenuItem(1L, "Burger", "Delicious beef burger", initialPrice);
        
        Money newPrice = new Money(new BigDecimal("14.99"));
        menuItem.updatePrice(newPrice);
        
        assertEquals(newPrice, menuItem.getPrice());
    }
    
    @Test
    void testUpdatePriceWithInvalidValue() {
        Money initialPrice = new Money(new BigDecimal("12.99"));
        MenuItem menuItem = new MenuItem(1L, "Burger", "Delicious beef burger", initialPrice);
        
        assertThrows(IllegalArgumentException.class, () -> {
            menuItem.updatePrice(new Money(BigDecimal.ZERO));
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            menuItem.updatePrice(null);
        });
    }
    
    @Test
    void testUpdateDetails() {
        Money initialPrice = new Money(new BigDecimal("12.99"));
        MenuItem menuItem = new MenuItem(1L, "Burger", "Delicious beef burger", initialPrice);
        
        Money newPrice = new Money(new BigDecimal("14.99"));
        menuItem.updateDetails("Cheeseburger", "Delicious beef burger with cheese", newPrice);
        
        assertEquals("Cheeseburger", menuItem.getName());
        assertEquals("Delicious beef burger with cheese", menuItem.getDescription());
        assertEquals(newPrice, menuItem.getPrice());
    }
    
    @Test
    void testUpdateDetailsWithPartialData() {
        Money initialPrice = new Money(new BigDecimal("12.99"));
        MenuItem menuItem = new MenuItem(1L, "Burger", "Delicious beef burger", initialPrice);
        
        // Update only name
        menuItem.updateDetails("Cheeseburger", null, null);
        assertEquals("Cheeseburger", menuItem.getName());
        assertEquals("Delicious beef burger", menuItem.getDescription());
        assertEquals(initialPrice, menuItem.getPrice());
        
        // Update only description
        menuItem.updateDetails(null, "Updated description", null);
        assertEquals("Cheeseburger", menuItem.getName());
        assertEquals("Updated description", menuItem.getDescription());
        assertEquals(initialPrice, menuItem.getPrice());
        
        // Update only price
        Money newPrice = new Money(new BigDecimal("14.99"));
        menuItem.updateDetails(null, null, newPrice);
        assertEquals("Cheeseburger", menuItem.getName());
        assertEquals("Updated description", menuItem.getDescription());
        assertEquals(newPrice, menuItem.getPrice());
    }
    
    @Test
    void testSetAvailable() {
        Money price = new Money(new BigDecimal("12.99"));
        MenuItem menuItem = new MenuItem(1L, "Burger", "Delicious beef burger", price);
        
        assertTrue(menuItem.isAvailable());
        
        menuItem.setAvailable(false);
        assertFalse(menuItem.isAvailable());
        
        menuItem.setAvailable(true);
        assertTrue(menuItem.isAvailable());
    }
    
    @Test
    void testPriceValidationInvariant() {
        // Test that price must always be positive
        Money validPrice = new Money(new BigDecimal("12.99"));
        MenuItem menuItem = new MenuItem(1L, "Burger", "Delicious beef burger", validPrice);
        
        // Try to update with invalid prices
        assertThrows(IllegalArgumentException.class, () -> {
            menuItem.updatePrice(new Money(BigDecimal.ZERO));
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            menuItem.updatePrice(new Money(new BigDecimal("-1.00")));
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            menuItem.updateDetails("New Name", "New Description", new Money(BigDecimal.ZERO));
        });
        
        // Price should remain unchanged after failed updates
        assertEquals(validPrice, menuItem.getPrice());
    }
}
