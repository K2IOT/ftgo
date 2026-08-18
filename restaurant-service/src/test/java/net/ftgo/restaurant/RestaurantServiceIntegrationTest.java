package net.ftgo.restaurant;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.ftgo.common.Address;
import net.ftgo.common.Money;
import net.ftgo.common.security.FtgoJwtAuthenticationConverter;
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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Integration tests for Restaurant Service REST and persistence behavior. */
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
        menuItemRepository.deleteAll();
        restaurantRepository.deleteAll();
    }

    @Test
    void testCreateRestaurantAndMenuItems() throws Exception {
        Address address = new Address("123 Main St", "San Francisco", "CA", "94102");
        CreateRestaurantRequest restaurantRequest = new CreateRestaurantRequest(
            "Test Restaurant",
            address,
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
        assertEquals("Burger", menuItems.get(0).getName());
        assertEquals(validPrice, menuItems.get(0).getPrice());
        assertTrue(menuItems.get(0).isAvailable());
    }

    @Test
    void testMenuItemPriceValidation_RejectsZeroPrice() throws Exception {
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
    void testMenuItemPriceValidation_RejectsNegativePrice() {
        Restaurant restaurant = createTestRestaurant();

        assertThrows(IllegalArgumentException.class, () -> new Money(new BigDecimal("-5.00")));
        assertThrows(IllegalArgumentException.class, () -> new MenuItem(
            restaurant.getId(),
            "Negative Item",
            "This should fail",
            new Money(new BigDecimal("-5.00"))
        ));
        assertEquals(0, menuItemRepository.findByRestaurantId(restaurant.getId()).size());
    }

    @Test
    void testMenuItemPriceValidation_AcceptsPositivePrice() throws Exception {
        Restaurant restaurant = createTestRestaurant();
        Money validPrice = new Money(new BigDecimal("15.99"));
        CreateMenuItemRequest request = new CreateMenuItemRequest(
            "Pizza",
            "Delicious cheese pizza",
            validPrice
        );

        mockMvc.perform(post("/restaurants/" + restaurant.getId() + "/menu-items")
                .with(authentication(restaurantAuthentication(restaurant.getId())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.price.amount").value(15.99));

        List<MenuItem> menuItems = menuItemRepository.findByRestaurantId(restaurant.getId());
        assertEquals(1, menuItems.size());
        assertEquals(validPrice, menuItems.get(0).getPrice());
    }

    @Test
    void testMenuItemAvailabilityCheck_DefaultAvailable() throws Exception {
        Restaurant restaurant = createTestRestaurant();
        CreateMenuItemRequest request = new CreateMenuItemRequest(
            "Salad",
            "Fresh garden salad",
            new Money(new BigDecimal("10.99"))
        );

        String response = mockMvc.perform(post("/restaurants/" + restaurant.getId() + "/menu-items")
                .with(authentication(restaurantAuthentication(restaurant.getId())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.available").value(true))
            .andReturn()
            .getResponse()
            .getContentAsString();

        Long menuItemId = objectMapper.readTree(response).get("id").asLong();
        assertTrue(menuItemRepository.findById(menuItemId).orElseThrow().isAvailable());
    }

    @Test
    void testMenuItemAvailabilityCheck_UpdateAvailability() throws Exception {
        Restaurant restaurant = createTestRestaurant();
        MenuItem menuItem = createTestMenuItem(restaurant.getId(), "Burger", new BigDecimal("12.99"));
        UpdateMenuItemRequest request = new UpdateMenuItemRequest(null, null, null, false);

        mockMvc.perform(put("/restaurants/" + restaurant.getId() + "/menu-items/" + menuItem.getId())
                .with(authentication(restaurantAuthentication(restaurant.getId())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.available").value(false));

        assertFalse(menuItemRepository.findById(menuItem.getId()).orElseThrow().isAvailable());
    }

    @Test
    void testMenuItemAvailabilityCheck_FilterByAvailability() {
        Restaurant restaurant = createTestRestaurant();
        createTestMenuItem(restaurant.getId(), "Burger", new BigDecimal("12.99"));
        MenuItem pizza = createTestMenuItem(restaurant.getId(), "Pizza", new BigDecimal("15.99"));
        MenuItem salad = createTestMenuItem(restaurant.getId(), "Salad", new BigDecimal("8.99"));

        pizza.setAvailable(false);
        menuItemRepository.save(pizza);

        List<MenuItem> available = menuItemRepository.findByRestaurantIdAndAvailable(restaurant.getId(), true);
        assertEquals(2, available.size());
        assertTrue(available.stream().allMatch(MenuItem::isAvailable));
        assertTrue(available.stream().noneMatch(item -> item.getName().equals("Pizza")));

        List<MenuItem> unavailable = menuItemRepository.findByRestaurantIdAndAvailable(restaurant.getId(), false);
        assertEquals(1, unavailable.size());
        assertEquals("Pizza", unavailable.get(0).getName());
        assertFalse(unavailable.get(0).isAvailable());
    }

    @Test
    void testMenuItemPriceUpdate_ValidatesPositivePrice() throws Exception {
        Restaurant restaurant = createTestRestaurant();
        MenuItem menuItem = createTestMenuItem(restaurant.getId(), "Burger", new BigDecimal("12.99"));
        UpdateMenuItemRequest request = new UpdateMenuItemRequest(
            null,
            null,
            new Money(BigDecimal.ZERO),
            null
        );

        mockMvc.perform(put("/restaurants/" + restaurant.getId() + "/menu-items/" + menuItem.getId())
                .with(authentication(restaurantAuthentication(restaurant.getId())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(containsString("positive")));

        assertEquals(
            new Money(new BigDecimal("12.99")),
            menuItemRepository.findById(menuItem.getId()).orElseThrow().getPrice()
        );
    }

    @Test
    void testMenuItemPriceUpdate_AcceptsValidPrice() throws Exception {
        Restaurant restaurant = createTestRestaurant();
        MenuItem menuItem = createTestMenuItem(restaurant.getId(), "Burger", new BigDecimal("12.99"));
        Money newPrice = new Money(new BigDecimal("14.99"));
        UpdateMenuItemRequest request = new UpdateMenuItemRequest(null, null, newPrice, null);

        mockMvc.perform(put("/restaurants/" + restaurant.getId() + "/menu-items/" + menuItem.getId())
                .with(authentication(restaurantAuthentication(restaurant.getId())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.price.amount").value(14.99));

        assertEquals(newPrice, menuItemRepository.findById(menuItem.getId()).orElseThrow().getPrice());
    }

    @Test
    void testCompleteRestaurantMenuWorkflow() {
        Restaurant restaurant = createTestRestaurant();
        createTestMenuItem(restaurant.getId(), "Burger", new BigDecimal("12.99"));
        MenuItem pizza = createTestMenuItem(restaurant.getId(), "Pizza", new BigDecimal("15.99"));
        MenuItem salad = createTestMenuItem(restaurant.getId(), "Salad", new BigDecimal("8.99"));

        assertEquals(3, menuItemRepository.findByRestaurantId(restaurant.getId()).size());
        assertTrue(menuItemRepository.findByRestaurantId(restaurant.getId()).stream().allMatch(MenuItem::isAvailable));

        pizza.updatePrice(new Money(new BigDecimal("17.99")));
        menuItemRepository.save(pizza);
        salad.setAvailable(false);
        menuItemRepository.save(salad);

        assertEquals(2, menuItemRepository.findByRestaurantIdAndAvailable(restaurant.getId(), true).size());
        assertEquals(
            new Money(new BigDecimal("17.99")),
            menuItemRepository.findById(pizza.getId()).orElseThrow().getPrice()
        );
        assertFalse(menuItemRepository.findById(salad.getId()).orElseThrow().isAvailable());
    }

    @Test
    void testMenuItemPriceValidation_PersistenceIntegrity() {
        Restaurant restaurant = createTestRestaurant();
        Money validPrice = new Money(new BigDecimal("20.00"));
        MenuItem menuItem = new MenuItem(
            restaurant.getId(),
            "Steak",
            "Premium ribeye steak",
            validPrice
        );
        menuItemRepository.save(menuItem);

        MenuItem retrieved = menuItemRepository.findById(menuItem.getId()).orElseThrow();
        assertEquals(validPrice, retrieved.getPrice());
        assertEquals(0, validPrice.getAmount().compareTo(retrieved.getPrice().getAmount()));
        assertTrue(retrieved.getPrice().getAmount().signum() > 0);
    }

    private Restaurant createTestRestaurant() {
        Restaurant restaurant = new Restaurant(
            "Test Restaurant",
            new Address("123 Main St", "San Francisco", "CA", "94102"),
            "{\"monday\": \"9:00-22:00\"}"
        );
        return restaurantRepository.save(restaurant);
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
