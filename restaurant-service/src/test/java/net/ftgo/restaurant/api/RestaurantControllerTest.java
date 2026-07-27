package net.ftgo.restaurant.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.ftgo.common.Address;
import net.ftgo.common.Money;
import net.ftgo.restaurant.domain.MenuItem;
import net.ftgo.restaurant.domain.Restaurant;
import net.ftgo.restaurant.security.RestaurantAuthorizationService;
import net.ftgo.restaurant.service.MenuItemNotFoundException;
import net.ftgo.restaurant.service.RestaurantNotFoundException;
import net.ftgo.restaurant.service.RestaurantService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Business-focused controller tests; authorization behavior has dedicated tests. */
@WebMvcTest(RestaurantController.class)
@AutoConfigureMockMvc(addFilters = false)
class RestaurantControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RestaurantService restaurantService;

    @MockBean
    private RestaurantAuthorizationService authorizationService;

    @Test
    void createRestaurant_shouldReturnCreated() throws Exception {
        Address address = new Address("123 Main St", "San Francisco", "CA", "94102");
        CreateRestaurantRequest request = new CreateRestaurantRequest(
            "Test Restaurant",
            address,
            "{\"monday\": \"9:00-22:00\"}"
        );
        Restaurant restaurant = new Restaurant("Test Restaurant", address, "{\"monday\": \"9:00-22:00\"}");
        when(restaurantService.createRestaurant(any(Restaurant.class))).thenReturn(restaurant);

        mockMvc.perform(post("/restaurants")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Test Restaurant"));

        verify(restaurantService).createRestaurant(any(Restaurant.class));
    }

    @Test
    void getRestaurant_shouldReturnRestaurant() throws Exception {
        long restaurantId = 1L;
        Restaurant restaurant = new Restaurant(
            "Test Restaurant",
            new Address("123 Main St", "San Francisco", "CA", "94102"),
            "{\"monday\": \"9:00-22:00\"}"
        );
        when(restaurantService.findRestaurant(restaurantId)).thenReturn(restaurant);

        mockMvc.perform(get("/restaurants/{restaurantId}", restaurantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Test Restaurant"));

        verify(restaurantService).findRestaurant(restaurantId);
    }

    @Test
    void getRestaurant_whenNotFound_shouldReturn404() throws Exception {
        long restaurantId = 999L;
        when(restaurantService.findRestaurant(restaurantId))
            .thenThrow(new RestaurantNotFoundException(restaurantId));

        mockMvc.perform(get("/restaurants/{restaurantId}", restaurantId))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("Restaurant not found"));
    }

    @Test
    void createMenuItem_shouldReturnCreated() throws Exception {
        long restaurantId = 1L;
        CreateMenuItemRequest request = new CreateMenuItemRequest(
            "Burger",
            "Delicious burger",
            new Money("12.99")
        );
        MenuItem menuItem = new MenuItem(restaurantId, "Burger", "Delicious burger", new Money("12.99"));
        when(restaurantService.createMenuItem(eq(restaurantId), any(MenuItem.class))).thenReturn(menuItem);

        mockMvc.perform(post("/restaurants/{restaurantId}/menu-items", restaurantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Burger"))
            .andExpect(jsonPath("$.price.amount").value(12.99));
    }

    @Test
    void getMenuItems_shouldReturnList() throws Exception {
        long restaurantId = 1L;
        when(restaurantService.getMenuItems(restaurantId)).thenReturn(List.of(
            new MenuItem(restaurantId, "Burger", "Delicious burger", new Money("12.99")),
            new MenuItem(restaurantId, "Fries", "Crispy fries", new Money("4.99"))
        ));

        mockMvc.perform(get("/restaurants/{restaurantId}/menu-items", restaurantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].name").value("Burger"))
            .andExpect(jsonPath("$[1].name").value("Fries"));
    }

    @Test
    void updateMenuItem_shouldReturnUpdated() throws Exception {
        long restaurantId = 1L;
        long menuItemId = 1L;
        UpdateMenuItemRequest request = new UpdateMenuItemRequest(
            "Updated Burger",
            "Even better burger",
            new Money("13.99"),
            false
        );
        MenuItem updated = new MenuItem(
            restaurantId,
            "Updated Burger",
            "Even better burger",
            new Money("13.99")
        );
        updated.setAvailable(false);
        when(restaurantService.updateMenuItem(
            eq(restaurantId),
            eq(menuItemId),
            anyString(),
            anyString(),
            any(Money.class),
            anyBoolean()
        )).thenReturn(updated);

        mockMvc.perform(put("/restaurants/{restaurantId}/menu-items/{menuItemId}", restaurantId, menuItemId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Updated Burger"))
            .andExpect(jsonPath("$.available").value(false));
    }

    @Test
    void deleteMenuItem_shouldReturnNoContent() throws Exception {
        doNothing().when(restaurantService).deleteMenuItem(1L, 1L);

        mockMvc.perform(delete("/restaurants/{restaurantId}/menu-items/{menuItemId}", 1L, 1L))
            .andExpect(status().isNoContent());
    }

    @Test
    void deleteMenuItem_whenNotFound_shouldReturn404() throws Exception {
        doThrow(new MenuItemNotFoundException(1L, 999L))
            .when(restaurantService).deleteMenuItem(1L, 999L);

        mockMvc.perform(delete("/restaurants/{restaurantId}/menu-items/{menuItemId}", 1L, 999L))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("Menu item not found"));
    }
}
