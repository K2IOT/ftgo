package net.ftgo.restaurant;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.ftgo.common.Address;
import net.ftgo.common.Money;
import net.ftgo.restaurant.api.CreateMenuItemRequest;
import net.ftgo.restaurant.api.CreateRestaurantRequest;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for Restaurant Service with Testcontainers and MySQL.
 * 
 * These tests use Testcontainers to spin up a real MySQL database,
 * providing production-like testing environment.
 * 
 * NOTE: Requires Docker to be running. If Docker is not available,
 * use RestaurantServiceIntegrationTest which uses H2 in-memory database.
 * 
 * Validates Requirements 5.1, 5.2, 5.3, 5.4
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("testcontainers")
class RestaurantServiceTestcontainersTest {
    
    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("restaurant_service_test")
            .withUsername("test")
            .withPassword("test");
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }
    
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
    void testCreateRestaurantAndMenuItemsWithMySQL() throws Exception {
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
        
        // Verify persistence in MySQL
        List<MenuItem> menuItems = menuItemRepository.findByRestaurantId(restaurantId);
        assertEquals(1, menuItems.size());
        assertEquals("Burger", menuItems.get(0).getName());
        assertEquals(validPrice, menuItems.get(0).getPrice());
        assertTrue(menuItems.get(0).isAvailable());
    }
    
    @Test
    void testMenuItemPriceValidationWithMySQL() throws Exception {
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
        
        // Verify no menu item was created in MySQL
        List<MenuItem> menuItems = menuItemRepository.findByRestaurantId(restaurant.getId());
        assertEquals(0, menuItems.size());
    }
    
    @Test
    void testMenuItemAvailabilityWithMySQL() throws Exception {
        // Create restaurant
        Restaurant restaurant = createTestRestaurant();
        
        // Create multiple menu items
        MenuItem item1 = createTestMenuItem(restaurant.getId(), "Burger", new BigDecimal("12.99"));
        MenuItem item2 = createTestMenuItem(restaurant.getId(), "Pizza", new BigDecimal("15.99"));
        MenuItem item3 = createTestMenuItem(restaurant.getId(), "Salad", new BigDecimal("8.99"));
        
        // Mark item2 as unavailable
        item2.setAvailable(false);
        menuItemRepository.save(item2);
        
        // Query available items from MySQL
        List<MenuItem> availableItems = menuItemRepository.findByRestaurantIdAndAvailable(
                restaurant.getId(), 
                true
        );
        assertEquals(2, availableItems.size());
        assertTrue(availableItems.stream().allMatch(MenuItem::isAvailable));
        assertTrue(availableItems.stream().noneMatch(item -> item.getName().equals("Pizza")));
        
        // Query unavailable items from MySQL
        List<MenuItem> unavailableItems = menuItemRepository.findByRestaurantIdAndAvailable(
                restaurant.getId(), 
                false
        );
        assertEquals(1, unavailableItems.size());
        assertEquals("Pizza", unavailableItems.get(0).getName());
        assertFalse(unavailableItems.get(0).isAvailable());
    }
    
    @Test
    void testFlywayMigrationWithMySQL() {
        // This test verifies that Flyway migrations work correctly with MySQL
        // If we reach this point, Flyway has successfully created the schema
        
        // Verify tables exist by attempting to save entities
        Restaurant restaurant = createTestRestaurant();
        assertNotNull(restaurant.getId());
        
        MenuItem menuItem = createTestMenuItem(restaurant.getId(), "Test Item", new BigDecimal("10.00"));
        assertNotNull(menuItem.getId());
        
        // Verify we can query the data
        Restaurant found = restaurantRepository.findById(restaurant.getId()).orElseThrow();
        assertEquals(restaurant.getName(), found.getName());
        
        MenuItem foundItem = menuItemRepository.findById(menuItem.getId()).orElseThrow();
        assertEquals(menuItem.getName(), foundItem.getName());
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
