package net.ftgo.restaurant.repository;

import net.ftgo.common.Address;
import net.ftgo.restaurant.domain.Restaurant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for RestaurantRepository.
 */
@DataJpaTest
@ActiveProfiles("test")
class RestaurantRepositoryTest {
    
    @Autowired
    private TestEntityManager entityManager;
    
    @Autowired
    private RestaurantRepository restaurantRepository;
    
    @Test
    void testSaveAndFindById() {
        Address address = new Address("123 Main St", "San Francisco", "CA", "94102");
        String openingHours = "{\"monday\": \"9:00-22:00\", \"tuesday\": \"9:00-22:00\"}";
        Restaurant restaurant = new Restaurant("Test Restaurant", address, openingHours);
        
        Restaurant saved = restaurantRepository.save(restaurant);
        entityManager.flush();
        
        assertNotNull(saved.getId());
        
        Optional<Restaurant> found = restaurantRepository.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals("Test Restaurant", found.get().getName());
        assertEquals(address, found.get().getAddress());
        assertEquals(openingHours, found.get().getOpeningHours());
    }
    
    @Test
    void testFindByName() {
        Address address = new Address("123 Main St", "San Francisco", "CA", "94102");
        String openingHours = "{\"monday\": \"9:00-22:00\"}";
        Restaurant restaurant = new Restaurant("Test Restaurant", address, openingHours);
        
        restaurantRepository.save(restaurant);
        entityManager.flush();
        
        Optional<Restaurant> found = restaurantRepository.findByName("Test Restaurant");
        assertTrue(found.isPresent());
        assertEquals("Test Restaurant", found.get().getName());
    }
    
    @Test
    void testFindByNameNotFound() {
        Optional<Restaurant> found = restaurantRepository.findByName("Nonexistent Restaurant");
        assertFalse(found.isPresent());
    }
    
    @Test
    void testExistsByName() {
        Address address = new Address("123 Main St", "San Francisco", "CA", "94102");
        String openingHours = "{\"monday\": \"9:00-22:00\"}";
        Restaurant restaurant = new Restaurant("Test Restaurant", address, openingHours);
        
        restaurantRepository.save(restaurant);
        entityManager.flush();
        
        assertTrue(restaurantRepository.existsByName("Test Restaurant"));
        assertFalse(restaurantRepository.existsByName("Nonexistent Restaurant"));
    }
    
    @Test
    void testUpdateRestaurant() {
        Address address = new Address("123 Main St", "San Francisco", "CA", "94102");
        String openingHours = "{\"monday\": \"9:00-22:00\"}";
        Restaurant restaurant = new Restaurant("Test Restaurant", address, openingHours);
        
        Restaurant saved = restaurantRepository.save(restaurant);
        entityManager.flush();
        
        // Update profile
        Address newAddress = new Address("456 Oak Ave", "Los Angeles", "CA", "90001");
        String newOpeningHours = "{\"monday\": \"10:00-23:00\"}";
        saved.updateProfile("Updated Restaurant", newAddress, newOpeningHours);
        restaurantRepository.save(saved);
        entityManager.flush();
        
        Optional<Restaurant> updated = restaurantRepository.findById(saved.getId());
        assertTrue(updated.isPresent());
        assertEquals("Updated Restaurant", updated.get().getName());
        assertEquals(newAddress, updated.get().getAddress());
        assertEquals(newOpeningHours, updated.get().getOpeningHours());
    }
    
    @Test
    void testAddressPersistence() {
        Address address = new Address("123 Main St", "San Francisco", "CA", "94102");
        String openingHours = "{\"monday\": \"9:00-22:00\"}";
        Restaurant restaurant = new Restaurant("Test Restaurant", address, openingHours);
        
        Restaurant saved = restaurantRepository.save(restaurant);
        entityManager.flush();
        entityManager.clear(); // Clear persistence context
        
        // Reload from database
        Optional<Restaurant> reloaded = restaurantRepository.findById(saved.getId());
        assertTrue(reloaded.isPresent());
        assertEquals("123 Main St", reloaded.get().getAddress().getStreet());
        assertEquals("San Francisco", reloaded.get().getAddress().getCity());
        assertEquals("CA", reloaded.get().getAddress().getState());
        assertEquals("94102", reloaded.get().getAddress().getZipCode());
    }
}
