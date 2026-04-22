package net.ftgo.restaurant;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.ftgo.common.Address;
import net.ftgo.common.Money;
import net.ftgo.restaurant.api.CreateMenuItemRequest;
import net.ftgo.restaurant.api.CreateRestaurantRequest;
import net.ftgo.restaurant.api.UpdateMenuItemRequest;
import net.ftgo.restaurant.domain.MenuItem;
import net.ftgo.restaurant.domain.Restaurant;
import net.ftgo.restaurant.repository.MenuItemRepository;
import net.ftgo.restaurant.repository.RestaurantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for Restaurant Service.
 * 
 * Tests the complete flow from REST API through service layer to database,
 * validating menu item price validation, availability checks, and persistence.
 * 
 * Uses H2 in-memory database for testing (configured in application-test.yml).
 * For production-like testing with MySQL, see RestaurantServiceTestcontainersTest.
 * 
 * Validates Requirements 5.1, 5.2, 5.3, 5.4
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RestaurantServiceIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private RestaurantRepository restaurantRepository;
    
    @Autowired
    private MenuItemRepository menuItemRepository;
    
    @BeforeEach
    void setUp() {
        // Clean up database before each test
        menuItemRepository.deleteAll();
        restaurantRepository.deleteAll();
    }
    
    @Test
    void testCreateRestaurantAndMenuItems() throws Exception {
        // Create restaurant
        Address address = new Address("123 Main St", "San Francisco", "CA", "94102");
        CreateRestaurantRequest restaurantRequest = new CreateRestaurantRequest(
                "Test Restaurant",
                address,
                "{\"monday\": \"9:00-22:00\"}"
        );
        
        String restaurantResponse = mockMvc.perform(post("/restaurants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(restaurantRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Test Restaurant"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        
        Long restaurantId = objectMapper.readTree(restaurantResponse).get("id").asLong();
        
        // Add menu item with valid price
        Money validPrice = new Money(new BigDecimal("12.99"));
        CreateMenuItemRequest menuItemRequest = new CreateMenuItemRequest(
                "Burger",
                "Delicious beef burger",
                validPrice
        );
        
        mockMvc.perform(post("/restaurants/" + restaurantId + "/menu-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(menuItemRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Burger"))
                .andExpect(jsonPath("$.price.amount").value(12.99))
                .andExpect(jsonPath("$.available").value(true));
        
        // Verify persistence
        List<MenuItem> menuItems = menuItemRepository.findByRestaurantId(restaurantId);
        assertEquals(1, menuItems.size());
        assertEquals("Burger", menuItems.get(0).getName());
        assertEquals(validPrice, menuItems.get(0).getPrice());
        assertTrue(menuItems.get(0).isAvailable());
    }
    
    @Test
    void testMenuItemPriceValidation_RejectsZeroPrice() throws Exception {
        // Create restaurant first
        Restaurant restaurant = createTestRestaurant();
        
        // Try to create menu item with zero price
        Money zeroPrice = new Money(BigDecimal.ZERO);
        CreateMenuItemRequest menuItemRequest = new CreateMenuItemRequest(
                "Free Item",
                "This should fail",
                zeroPrice
        );
        
        mockMvc.perform(post("/restaurants/" + restaurant.getId() + "/menu-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(menuItemRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("positive")));
        
        // Verify no menu item was created
        List<MenuItem> menuItems = menuItemRepository.findByRestaurantId(restaurant.getId());
        assertEquals(0, menuItems.size());
    }
    
    @Test
    void testMenuItemPriceValidation_RejectsNegativePrice() throws Exception {
        // Create restaurant first
        Restaurant restaurant = createTestRestaurant();
        
        // The Money class itself validates that amount cannot be negative
        // This test verifies that the domain model enforces this constraint
        assertThrows(IllegalArgumentException.class, () -> {
            new Money(new BigDecimal("-5.00"));
        });
        
        // Also verify that trying to create a MenuItem with negative price fails
        assertThrows(IllegalArgumentException.class, () -> {
            new MenuItem(
                restaurant.getId(),
                "Negative Item",
                "This should fail",
                new Money(new BigDecimal("-5.00"))
            );
        });
        
        // Verify no menu item was created
        List<MenuItem> menuItems = menuItemRepository.findByRestaurantId(restaurant.getId());
        assertEquals(0, menuItems.size());
    }
    
    @Test
    void testMenuItemPriceValidation_AcceptsPositivePrice() throws Exception {
        // Create restaurant first
        Restaurant restaurant = createTestRestaurant();
        
        // Create menu item with valid positive price
        Money validPrice = new Money(new BigDecimal("15.99"));
        CreateMenuItemRequest menuItemRequest = new CreateMenuItemRequest(
                "Pizza",
                "Delicious cheese pizza",
                validPrice
        );
        
        mockMvc.perform(post("/restaurants/" + restaurant.getId() + "/menu-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(menuItemRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.price.amount").value(15.99));
        
        // Verify menu item was created with correct price
        List<MenuItem> menuItems = menuItemRepository.findByRestaurantId(restaurant.getId());
        assertEquals(1, menuItems.size());
        assertEquals(validPrice, menuItems.get(0).getPrice());
    }
    
    @Test
    void testMenuItemAvailabilityCheck_DefaultAvailable() throws Exception {
        // Create restaurant first
        Restaurant restaurant = createTestRestaurant();
        
        // Create menu item (should be available by default)
        Money price = new Money(new BigDecimal("10.99"));
        CreateMenuItemRequest menuItemRequest = new CreateMenuItemRequest(
                "Salad",
                "Fresh garden salad",
                price
        );
        
        String response = mockMvc.perform(post("/restaurants/" + restaurant.getId() + "/menu-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(menuItemRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.available").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();
        
        Long menuItemId = objectMapper.readTree(response).get("id").asLong();
        
        // Verify availability in database
        MenuItem menuItem = menuItemRepository.findById(menuItemId).orElseThrow();
        assertTrue(menuItem.isAvailable());
    }
    
    @Test
    void testMenuItemAvailabilityCheck_UpdateAvailability() throws Exception {
        // Create restaurant and menu item
        Restaurant restaurant = createTestRestaurant();
        MenuItem menuItem = createTestMenuItem(restaurant.getId(), "Burger", new BigDecimal("12.99"));
        
        // Update menu item to unavailable
        UpdateMenuItemRequest updateRequest = new UpdateMenuItemRequest(
                null,
                null,
                null,
                false
        );
        
        mockMvc.perform(put("/restaurants/" + restaurant.getId() + "/menu-items/" + menuItem.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false));
        
        // Verify availability was updated in database
        MenuItem updated = menuItemRepository.findById(menuItem.getId()).orElseThrow();
        assertFalse(updated.isAvailable());
    }
    
    @Test
    void testMenuItemAvailabilityCheck_FilterByAvailability() throws Exception {
        // Create restaurant
        Restaurant restaurant = createTestRestaurant();
        
        // Create multiple menu items with different availability
        MenuItem item1 = createTestMenuItem(restaurant.getId(), "Burger", new BigDecimal("12.99"));
        MenuItem item2 = createTestMenuItem(restaurant.getId(), "Pizza", new BigDecimal("15.99"));
        MenuItem item3 = createTestMenuItem(restaurant.getId(), "Salad", new BigDecimal("8.99"));
        
        // Mark item2 as unavailable
        item2.setAvailable(false);
        menuItemRepository.save(item2);
        
        // Query available items
        List<MenuItem> availableItems = menuItemRepository.findByRestaurantIdAndAvailable(
                restaurant.getId(), 
                true
        );
        assertEquals(2, availableItems.size());
        assertTrue(availableItems.stream().allMatch(MenuItem::isAvailable));
        assertTrue(availableItems.stream().noneMatch(item -> item.getName().equals("Pizza")));
        
        // Query unavailable items
        List<MenuItem> unavailableItems = menuItemRepository.findByRestaurantIdAndAvailable(
                restaurant.getId(), 
                false
        );
        assertEquals(1, unavailableItems.size());
        assertEquals("Pizza", unavailableItems.get(0).getName());
        assertFalse(unavailableItems.get(0).isAvailable());
    }
    
    @Test
    void testMenuItemPriceUpdate_ValidatesPositivePrice() throws Exception {
        // Create restaurant and menu item
        Restaurant restaurant = createTestRestaurant();
        MenuItem menuItem = createTestMenuItem(restaurant.getId(), "Burger", new BigDecimal("12.99"));
        
        // Try to update with zero price
        UpdateMenuItemRequest updateRequest = new UpdateMenuItemRequest(
                null,
                null,
                new Money(BigDecimal.ZERO),
                null
        );
        
        mockMvc.perform(put("/restaurants/" + restaurant.getId() + "/menu-items/" + menuItem.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("positive")));
        
        // Verify price was not updated
        MenuItem unchanged = menuItemRepository.findById(menuItem.getId()).orElseThrow();
        assertEquals(new Money(new BigDecimal("12.99")), unchanged.getPrice());
    }
    
    @Test
    void testMenuItemPriceUpdate_AcceptsValidPrice() throws Exception {
        // Create restaurant and menu item
        Restaurant restaurant = createTestRestaurant();
        MenuItem menuItem = createTestMenuItem(restaurant.getId(), "Burger", new BigDecimal("12.99"));
        
        // Update with valid price
        Money newPrice = new Money(new BigDecimal("14.99"));
        UpdateMenuItemRequest updateRequest = new UpdateMenuItemRequest(
                null,
                null,
                newPrice,
                null
        );
        
        mockMvc.perform(put("/restaurants/" + restaurant.getId() + "/menu-items/" + menuItem.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price.amount").value(14.99));
        
        // Verify price was updated
        MenuItem updated = menuItemRepository.findById(menuItem.getId()).orElseThrow();
        assertEquals(newPrice, updated.getPrice());
    }
    
    @Test
    void testCompleteRestaurantMenuWorkflow() throws Exception {
        // 1. Create restaurant
        Restaurant restaurant = createTestRestaurant();
        
        // 2. Add multiple menu items
        MenuItem burger = createTestMenuItem(restaurant.getId(), "Burger", new BigDecimal("12.99"));
        MenuItem pizza = createTestMenuItem(restaurant.getId(), "Pizza", new BigDecimal("15.99"));
        MenuItem salad = createTestMenuItem(restaurant.getId(), "Salad", new BigDecimal("8.99"));
        
        // 3. Verify all items are available by default
        List<MenuItem> allItems = menuItemRepository.findByRestaurantId(restaurant.getId());
        assertEquals(3, allItems.size());
        assertTrue(allItems.stream().allMatch(MenuItem::isAvailable));
        
        // 4. Update pizza price
        pizza.updatePrice(new Money(new BigDecimal("17.99")));
        menuItemRepository.save(pizza);
        
        // 5. Mark salad as unavailable
        salad.setAvailable(false);
        menuItemRepository.save(salad);
        
        // 6. Verify final state
        List<MenuItem> availableItems = menuItemRepository.findByRestaurantIdAndAvailable(
                restaurant.getId(), 
                true
        );
        assertEquals(2, availableItems.size());
        
        MenuItem updatedPizza = menuItemRepository.findById(pizza.getId()).orElseThrow();
        assertEquals(new Money(new BigDecimal("17.99")), updatedPizza.getPrice());
        
        MenuItem updatedSalad = menuItemRepository.findById(salad.getId()).orElseThrow();
        assertFalse(updatedSalad.isAvailable());
    }
    
    @Test
    void testMenuItemPriceValidation_PersistenceIntegrity() throws Exception {
        // Create restaurant
        Restaurant restaurant = createTestRestaurant();
        
        // Create menu item with valid price
        Money validPrice = new Money(new BigDecimal("20.00"));
        MenuItem menuItem = new MenuItem(
                restaurant.getId(),
                "Steak",
                "Premium ribeye steak",
                validPrice
        );
        menuItemRepository.save(menuItem);
        
        // Retrieve from database and verify price integrity
        MenuItem retrieved = menuItemRepository.findById(menuItem.getId()).orElseThrow();
        assertEquals(validPrice, retrieved.getPrice());
        assertEquals(0, validPrice.getAmount().compareTo(retrieved.getPrice().getAmount()));
        
        // Verify price is positive
        assertTrue(retrieved.getPrice().getAmount().signum() > 0);
    }
    
    // Helper methods
    
    private Restaurant createTestRestaurant() {
        Address address = new Address("123 Main St", "San Francisco", "CA", "94102");
        Restaurant restaurant = new Restaurant(
                "Test Restaurant",
                address,
                "{\"monday\": \"9:00-22:00\"}"
        );
        return restaurantRepository.save(restaurant);
    }
    
    private MenuItem createTestMenuItem(Long restaurantId, String name, BigDecimal price) {
        MenuItem menuItem = new MenuItem(
                restaurantId,
                name,
                "Test description for " + name,
                new Money(price)
        );
        return menuItemRepository.save(menuItem);
    }
}
