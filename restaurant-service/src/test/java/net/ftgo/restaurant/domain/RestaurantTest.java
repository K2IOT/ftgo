package net.ftgo.restaurant.domain;

import net.ftgo.common.Address;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Restaurant aggregate.
 */
class RestaurantTest {
    
    @Test
    void testCreateRestaurant() {
        Address address = new Address("123 Main St", "San Francisco", "CA", "94102");
        String openingHours = "{\"monday\": \"9:00-22:00\", \"tuesday\": \"9:00-22:00\"}";
        
        Restaurant restaurant = new Restaurant("Test Restaurant", address, openingHours);
        
        assertEquals("Test Restaurant", restaurant.getName());
        assertEquals(address, restaurant.getAddress());
        assertEquals(openingHours, restaurant.getOpeningHours());
        assertNotNull(restaurant.getCreatedAt());
        assertNotNull(restaurant.getUpdatedAt());
    }
    
    @Test
    void testCreateRestaurantWithNullName() {
        Address address = new Address("123 Main St", "San Francisco", "CA", "94102");
        String openingHours = "{\"monday\": \"9:00-22:00\"}";
        
        assertThrows(IllegalArgumentException.class, () -> {
            new Restaurant(null, address, openingHours);
        });
    }
    
    @Test
    void testCreateRestaurantWithBlankName() {
        Address address = new Address("123 Main St", "San Francisco", "CA", "94102");
        String openingHours = "{\"monday\": \"9:00-22:00\"}";
        
        assertThrows(IllegalArgumentException.class, () -> {
            new Restaurant("   ", address, openingHours);
        });
    }
    
    @Test
    void testCreateRestaurantWithNullAddress() {
        String openingHours = "{\"monday\": \"9:00-22:00\"}";
        
        assertThrows(IllegalArgumentException.class, () -> {
            new Restaurant("Test Restaurant", null, openingHours);
        });
    }
    
    @Test
    void testCreateRestaurantWithNullOpeningHours() {
        Address address = new Address("123 Main St", "San Francisco", "CA", "94102");
        
        assertThrows(IllegalArgumentException.class, () -> {
            new Restaurant("Test Restaurant", address, null);
        });
    }
    
    @Test
    void testCreateRestaurantWithBlankOpeningHours() {
        Address address = new Address("123 Main St", "San Francisco", "CA", "94102");
        
        assertThrows(IllegalArgumentException.class, () -> {
            new Restaurant("Test Restaurant", address, "   ");
        });
    }
    
    @Test
    void testUpdateProfile() {
        Address address = new Address("123 Main St", "San Francisco", "CA", "94102");
        String openingHours = "{\"monday\": \"9:00-22:00\"}";
        Restaurant restaurant = new Restaurant("Test Restaurant", address, openingHours);
        
        Address newAddress = new Address("456 Oak Ave", "Los Angeles", "CA", "90001");
        String newOpeningHours = "{\"monday\": \"10:00-23:00\"}";
        
        restaurant.updateProfile("Updated Restaurant", newAddress, newOpeningHours);
        
        assertEquals("Updated Restaurant", restaurant.getName());
        assertEquals(newAddress, restaurant.getAddress());
        assertEquals(newOpeningHours, restaurant.getOpeningHours());
    }
    
    @Test
    void testUpdateProfileWithPartialData() {
        Address address = new Address("123 Main St", "San Francisco", "CA", "94102");
        String openingHours = "{\"monday\": \"9:00-22:00\"}";
        Restaurant restaurant = new Restaurant("Test Restaurant", address, openingHours);
        
        // Update only name
        restaurant.updateProfile("New Name", null, null);
        assertEquals("New Name", restaurant.getName());
        assertEquals(address, restaurant.getAddress());
        assertEquals(openingHours, restaurant.getOpeningHours());
        
        // Update only address
        Address newAddress = new Address("456 Oak Ave", "Los Angeles", "CA", "90001");
        restaurant.updateProfile(null, newAddress, null);
        assertEquals("New Name", restaurant.getName());
        assertEquals(newAddress, restaurant.getAddress());
        assertEquals(openingHours, restaurant.getOpeningHours());
        
        // Update only opening hours
        String newOpeningHours = "{\"monday\": \"10:00-23:00\"}";
        restaurant.updateProfile(null, null, newOpeningHours);
        assertEquals("New Name", restaurant.getName());
        assertEquals(newAddress, restaurant.getAddress());
        assertEquals(newOpeningHours, restaurant.getOpeningHours());
    }
    
    @Test
    void testUpdateProfileWithBlankName() {
        Address address = new Address("123 Main St", "San Francisco", "CA", "94102");
        String openingHours = "{\"monday\": \"9:00-22:00\"}";
        Restaurant restaurant = new Restaurant("Test Restaurant", address, openingHours);
        
        // Blank name should not update
        restaurant.updateProfile("   ", null, null);
        assertEquals("Test Restaurant", restaurant.getName());
    }
}
