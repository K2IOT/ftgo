package net.ftgo.restaurant.repository;

import net.ftgo.common.Address;
import net.ftgo.common.Money;
import net.ftgo.restaurant.domain.MenuItem;
import net.ftgo.restaurant.domain.Restaurant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for MenuItemRepository.
 */
@DataJpaTest
@ActiveProfiles("test")
class MenuItemRepositoryTest {
    
    @Autowired
    private TestEntityManager entityManager;
    
    @Autowired
    private MenuItemRepository menuItemRepository;
    
    @Autowired
    private RestaurantRepository restaurantRepository;
    
    private Long restaurantId;
    
    @BeforeEach
    void setUp() {
        // Create a restaurant for testing
        Address address = new Address("123 Main St", "San Francisco", "CA", "94102");
        String openingHours = "{\"monday\": \"9:00-22:00\"}";
        Restaurant restaurant = new Restaurant("Test Restaurant", address, openingHours);
        Restaurant saved = restaurantRepository.save(restaurant);
        entityManager.flush();
        restaurantId = saved.getId();
    }
    
    @Test
    void testSaveAndFindById() {
        Money price = new Money(new BigDecimal("12.99"));
        MenuItem menuItem = new MenuItem(restaurantId, "Burger", "Delicious beef burger", price);
        
        MenuItem saved = menuItemRepository.save(menuItem);
        entityManager.flush();
        
        assertNotNull(saved.getId());
        
        Optional<MenuItem> found = menuItemRepository.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals("Burger", found.get().getName());
        assertEquals("Delicious beef burger", found.get().getDescription());
        assertEquals(price, found.get().getPrice());
        assertTrue(found.get().isAvailable());
    }
    
    @Test
    void testFindByRestaurantId() {
        Money price1 = new Money(new BigDecimal("12.99"));
        Money price2 = new Money(new BigDecimal("8.99"));
        Money price3 = new Money(new BigDecimal("15.99"));
        
        MenuItem menuItem1 = new MenuItem(restaurantId, "Burger", "Delicious beef burger", price1);
        MenuItem menuItem2 = new MenuItem(restaurantId, "Fries", "Crispy french fries", price2);
        MenuItem menuItem3 = new MenuItem(restaurantId, "Pizza", "Cheese pizza", price3);
        
        menuItemRepository.save(menuItem1);
        menuItemRepository.save(menuItem2);
        menuItemRepository.save(menuItem3);
        entityManager.flush();
        
        List<MenuItem> items = menuItemRepository.findByRestaurantId(restaurantId);
        assertEquals(3, items.size());
    }
    
    @Test
    void testFindByRestaurantIdAndAvailable() {
        Money price1 = new Money(new BigDecimal("12.99"));
        Money price2 = new Money(new BigDecimal("8.99"));
        Money price3 = new Money(new BigDecimal("15.99"));
        
        MenuItem menuItem1 = new MenuItem(restaurantId, "Burger", "Delicious beef burger", price1);
        MenuItem menuItem2 = new MenuItem(restaurantId, "Fries", "Crispy french fries", price2);
        MenuItem menuItem3 = new MenuItem(restaurantId, "Pizza", "Cheese pizza", price3);
        
        menuItem2.setAvailable(false); // Mark fries as unavailable
        
        menuItemRepository.save(menuItem1);
        menuItemRepository.save(menuItem2);
        menuItemRepository.save(menuItem3);
        entityManager.flush();
        
        List<MenuItem> availableItems = menuItemRepository.findByRestaurantIdAndAvailable(restaurantId, true);
        assertEquals(2, availableItems.size());
        assertTrue(availableItems.stream().noneMatch(item -> item.getName().equals("Fries")));
        
        List<MenuItem> unavailableItems = menuItemRepository.findByRestaurantIdAndAvailable(restaurantId, false);
        assertEquals(1, unavailableItems.size());
        assertEquals("Fries", unavailableItems.get(0).getName());
    }
    
    @Test
    void testFindByRestaurantIdAndId() {
        Money price = new Money(new BigDecimal("12.99"));
        MenuItem menuItem = new MenuItem(restaurantId, "Burger", "Delicious beef burger", price);
        
        MenuItem saved = menuItemRepository.save(menuItem);
        entityManager.flush();
        
        Optional<MenuItem> found = menuItemRepository.findByRestaurantIdAndId(restaurantId, saved.getId());
        assertTrue(found.isPresent());
        assertEquals("Burger", found.get().getName());
        
        // Try with wrong restaurant ID
        Optional<MenuItem> notFound = menuItemRepository.findByRestaurantIdAndId(999L, saved.getId());
        assertFalse(notFound.isPresent());
    }
    
    @Test
    void testExistsByRestaurantIdAndId() {
        Money price = new Money(new BigDecimal("12.99"));
        MenuItem menuItem = new MenuItem(restaurantId, "Burger", "Delicious beef burger", price);
        
        MenuItem saved = menuItemRepository.save(menuItem);
        entityManager.flush();
        
        assertTrue(menuItemRepository.existsByRestaurantIdAndId(restaurantId, saved.getId()));
        assertFalse(menuItemRepository.existsByRestaurantIdAndId(999L, saved.getId()));
        assertFalse(menuItemRepository.existsByRestaurantIdAndId(restaurantId, 999L));
    }
    
    @Test
    void testUpdateMenuItem() {
        Money price = new Money(new BigDecimal("12.99"));
        MenuItem menuItem = new MenuItem(restaurantId, "Burger", "Delicious beef burger", price);
        
        MenuItem saved = menuItemRepository.save(menuItem);
        entityManager.flush();
        
        // Update details
        Money newPrice = new Money(new BigDecimal("14.99"));
        saved.updateDetails("Cheeseburger", "Delicious beef burger with cheese", newPrice);
        menuItemRepository.save(saved);
        entityManager.flush();
        
        Optional<MenuItem> updated = menuItemRepository.findById(saved.getId());
        assertTrue(updated.isPresent());
        assertEquals("Cheeseburger", updated.get().getName());
        assertEquals("Delicious beef burger with cheese", updated.get().getDescription());
        assertEquals(newPrice, updated.get().getPrice());
    }
    
    @Test
    void testSetAvailabilityPersistence() {
        Money price = new Money(new BigDecimal("12.99"));
        MenuItem menuItem = new MenuItem(restaurantId, "Burger", "Delicious beef burger", price);
        
        MenuItem saved = menuItemRepository.save(menuItem);
        entityManager.flush();
        
        // Set unavailable
        saved.setAvailable(false);
        menuItemRepository.save(saved);
        entityManager.flush();
        entityManager.clear(); // Clear persistence context
        
        // Reload from database
        Optional<MenuItem> reloaded = menuItemRepository.findById(saved.getId());
        assertTrue(reloaded.isPresent());
        assertFalse(reloaded.get().isAvailable());
    }
    
    @Test
    void testPriceValidationOnSave() {
        Money validPrice = new Money(new BigDecimal("12.99"));
        MenuItem menuItem = new MenuItem(restaurantId, "Burger", "Delicious beef burger", validPrice);
        
        MenuItem saved = menuItemRepository.save(menuItem);
        entityManager.flush();
        
        assertNotNull(saved.getId());
        assertEquals(validPrice, saved.getPrice());
    }
}
