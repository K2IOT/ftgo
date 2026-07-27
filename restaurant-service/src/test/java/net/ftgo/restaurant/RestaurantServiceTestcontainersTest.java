package net.ftgo.restaurant;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.ftgo.common.Address;
import net.ftgo.common.Money;
import net.ftgo.common.security.FtgoJwtAuthenticationConverter;
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
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
        menuItemRepository.deleteAll();
        restaurantRepository.deleteAll();
    }

    @Test
    void testCreateRestaurantAndMenuItemsWithMySQL() throws Exception {
        CreateRestaurantRequest restaurantRequest = new CreateRestaurantRequest(
            "Test Restaurant",
            new Address("123 Main St", "San Francisco", "CA", "94102"),
            "{\"monday\": \"9:00-22:00\"}"
        );

        String restaurantResponse = mockMvc.perform(post("/restaurants")
                .with(authentication(adminAuthentication()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(restaurantRequest)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.name").value("Test Restaurant"))
            .andReturn()
            .getResponse()
            .getContentAsString();

        Long restaurantId = objectMapper.readTree(restaurantResponse).get("id").asLong();
        Money validPrice = new Money(new BigDecimal("12.99"));
        CreateMenuItemRequest menuItemRequest = new CreateMenuItemRequest(
            "Burger",
            "Delicious beef burger",
            validPrice
        );

        mockMvc.perform(post("/restaurants/" + restaurantId + "/menu-items")
                .with(authentication(restaurantAuthentication(restaurantId)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(menuItemRequest)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.name").value("Burger"))
            .andExpect(jsonPath("$.price.amount").value(12.99))
            .andExpect(jsonPath("$.available").value(true));

        List<MenuItem> menuItems = menuItemRepository.findByRestaurantId(restaurantId);
        assertEquals(1, menuItems.size());
        assertEquals("Burger", menuItems.getFirst().getName());
        assertEquals(validPrice, menuItems.getFirst().getPrice());
        assertTrue(menuItems.getFirst().isAvailable());
    }

    @Test
    void testMenuItemPriceValidationWithMySQL() throws Exception {
        Restaurant restaurant = createTestRestaurant();
        CreateMenuItemRequest request = new CreateMenuItemRequest(
            "Free Item",
            "This should fail",
            new Money(BigDecimal.ZERO)
        );

        mockMvc.perform(post("/restaurants/" + restaurant.getId() + "/menu-items")
                .with(authentication(restaurantAuthentication(restaurant.getId())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(containsString("positive")));

        assertEquals(0, menuItemRepository.findByRestaurantId(restaurant.getId()).size());
    }

    @Test
    void testMenuItemAvailabilityWithMySQL() {
        Restaurant restaurant = createTestRestaurant();
        createTestMenuItem(restaurant.getId(), "Burger", new BigDecimal("12.99"));
        MenuItem pizza = createTestMenuItem(restaurant.getId(), "Pizza", new BigDecimal("15.99"));
        createTestMenuItem(restaurant.getId(), "Salad", new BigDecimal("8.99"));
        pizza.setAvailable(false);
        menuItemRepository.save(pizza);

        List<MenuItem> available = menuItemRepository.findByRestaurantIdAndAvailable(restaurant.getId(), true);
        assertEquals(2, available.size());
        assertTrue(available.stream().allMatch(MenuItem::isAvailable));
        assertTrue(available.stream().noneMatch(item -> item.getName().equals("Pizza")));

        List<MenuItem> unavailable = menuItemRepository.findByRestaurantIdAndAvailable(restaurant.getId(), false);
        assertEquals(1, unavailable.size());
        assertEquals("Pizza", unavailable.getFirst().getName());
        assertFalse(unavailable.getFirst().isAvailable());
    }

    @Test
    void testFlywayMigrationWithMySQL() {
        Restaurant restaurant = createTestRestaurant();
        assertNotNull(restaurant.getId());
        MenuItem menuItem = createTestMenuItem(restaurant.getId(), "Test Item", new BigDecimal("10.00"));
        assertNotNull(menuItem.getId());
        assertEquals(restaurant.getName(), restaurantRepository.findById(restaurant.getId()).orElseThrow().getName());
        assertEquals(menuItem.getName(), menuItemRepository.findById(menuItem.getId()).orElseThrow().getName());
    }

    private Restaurant createTestRestaurant() {
        return restaurantRepository.save(new Restaurant(
            "Test Restaurant",
            new Address("123 Main St", "San Francisco", "CA", "94102"),
            "{\"monday\": \"9:00-22:00\"}"
        ));
    }

    private MenuItem createTestMenuItem(Long restaurantId, String name, BigDecimal price) {
        return menuItemRepository.save(new MenuItem(
            restaurantId,
            name,
            "Test description for " + name,
            new Money(price)
        ));
    }

    private AbstractAuthenticationToken adminAuthentication() {
        return ftgoAuthentication("ADMIN", List.of());
    }

    private AbstractAuthenticationToken restaurantAuthentication(Long restaurantId) {
        return ftgoAuthentication("RESTAURANT", List.of(restaurantId));
    }

    private AbstractAuthenticationToken ftgoAuthentication(String role, List<Long> restaurantIds) {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue(role.toLowerCase() + "-token")
            .header("alg", "RS256")
            .subject(role.toLowerCase() + "-user")
            .issuedAt(now)
            .expiresAt(now.plusSeconds(300))
            .audience(List.of("ftgo-api"))
            .claim("roles", List.of(role))
            .claim("restaurant_ids", restaurantIds)
            .build();
        return new FtgoJwtAuthenticationConverter("").convert(jwt);
    }
}
