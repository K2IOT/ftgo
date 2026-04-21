package net.ftgo.restaurant.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.ftgo.common.Address;
import net.ftgo.common.Money;
import net.ftgo.restaurant.domain.MenuItem;
import net.ftgo.restaurant.domain.Restaurant;
import net.ftgo.restaurant.service.MenuItemNotFoundException;
import net.ftgo.restaurant.service.RestaurantNotFoundException;
import net.ftgo.restaurant.service.RestaurantService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for RestaurantController.
 */
@WebMvcTest(RestaurantController.class)
class RestaurantControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @MockBean
    private RestaurantService restaurantService;
    
    @Test
    void createRestaurant_shouldReturnCreated() throws Exception {
        // Given
        Address address = new Address("123 Main St", "San Francisco", "CA", "94102");
        CreateRestaurantRequest request = new CreateRestaurantRequest(
            "Test Restaurant",
            address,
            "{\"monday\": \"9:00-22:00\"}"
        );
        
        Restaurant restaurant = new Restaurant("Test Restaurant", address, "{\"monday\": \"9:00-22:00\"}");
        when(restaurantService.createRestaurant(any(Restaurant.class))).thenReturn(restaurant);
        
        // When & Then
        mockMvc.perform(post("/restaurants")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Test Restaurant"));
        
        verify(restaurantService).createRestaurant(any(Restaurant.class));
    }
    
    @Test
    void getRestaurant_shouldReturnRestaurant() throws Exception {
        // Given
        Long restaurantId = 1L;
        Address address = new Address("123 Main St", "San Francisco", "CA", "94102");
        Restaurant restaurant = new Restaurant("Test Restaurant", address, "{\"monday\": \"9:00-22:00\"}");
        
        when(restaurantService.findRestaurant(restaurantId)).thenReturn(restaurant);
        
        // When & Then
        mockMvc.perform(get("/restaurants/{restaurantId}", restaurantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Test Restaurant"));
        
        verify(restaurantService).findRestaurant(restaurantId);
    }
    
    @Test
    void getRestaurant_whenNotFound_shouldReturn404() throws Exception {
        // Given
        Long restaurantId = 999L;
        when(restaurantService.findRestaurant(restaurantId))
            .thenThrow(new RestaurantNotFoundException(restaurantId));
        
        // When & Then
        mockMvc.perform(get("/restaurants/{restaurantId}", restaurantId))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("Restaurant not found"));
        
        verify(restaurantService).findRestaurant(restaurantId);
    }
    
    @Test
    void createMenuItem_shouldReturnCreated() throws Exception {
        // Given
        Long restaurantId = 1L;
        CreateMenuItemRequest request = new CreateMenuItemRequest(
            "Burger",
            "Delicious burger",
            new Money("12.99")
        );
        
        MenuItem menuItem = new MenuItem(restaurantId, "Burger", "Delicious burger", new Money("12.99"));
        when(restaurantService.createMenuItem(eq(restaurantId), any(MenuItem.class)))
            .thenReturn(menuItem);
        
        // When & Then
        mockMvc.perform(post("/restaurants/{restaurantId}/menu-items", restaurantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Burger"))
            .andExpect(jsonPath("$.price.amount").value(12.99));
        
        verify(restaurantService).createMenuItem(eq(restaurantId), any(MenuItem.class));
    }
    
    @Test
    void getMenuItems_shouldReturnList() throws Exception {
        // Given
        Long restaurantId = 1L;
        MenuItem item1 = new MenuItem(restaurantId, "Burger", "Delicious burger", new Money("12.99"));
        MenuItem item2 = new MenuItem(restaurantId, "Fries", "Crispy fries", new Money("4.99"));
        List<MenuItem> menuItems = Arrays.asList(item1, item2);
        
        when(restaurantService.getMenuItems(restaurantId)).thenReturn(menuItems);
        
        // When & Then
        mockMvc.perform(get("/restaurants/{restaurantId}/menu-items", restaurantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].name").value("Burger"))
            .andExpect(jsonPath("$[1].name").value("Fries"));
        
        verify(restaurantService).getMenuItems(restaurantId);
    }
    
    @Test
    void updateMenuItem_shouldReturnUpdated() throws Exception {
        // Given
        Long restaurantId = 1L;
        Long menuItemId = 1L;
        UpdateMenuItemRequest request = new UpdateMenuItemRequest(
            "Updated Burger",
            "Even better burger",
            new Money("13.99"),
            false
        );
        
        MenuItem updated = new MenuItem(restaurantId, "Updated Burger", "Even better burger", new Money("13.99"));
        updated.setAvailable(false);
        
        when(restaurantService.updateMenuItem(
            eq(restaurantId), eq(menuItemId), anyString(), anyString(), any(Money.class), anyBoolean()))
            .thenReturn(updated);
        
        // When & Then
        mockMvc.perform(put("/restaurants/{restaurantId}/menu-items/{menuItemId}", restaurantId, menuItemId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Updated Burger"))
            .andExpect(jsonPath("$.available").value(false));
        
        verify(restaurantService).updateMenuItem(
            eq(restaurantId), eq(menuItemId), anyString(), anyString(), any(Money.class), anyBoolean());
    }
    
    @Test
    void deleteMenuItem_shouldReturnNoContent() throws Exception {
        // Given
        Long restaurantId = 1L;
        Long menuItemId = 1L;
        
        doNothing().when(restaurantService).deleteMenuItem(restaurantId, menuItemId);
        
        // When & Then
        mockMvc.perform(delete("/restaurants/{restaurantId}/menu-items/{menuItemId}", restaurantId, menuItemId))
            .andExpect(status().isNoContent());
        
        verify(restaurantService).deleteMenuItem(restaurantId, menuItemId);
    }
    
    @Test
    void deleteMenuItem_whenNotFound_shouldReturn404() throws Exception {
        // Given
        Long restaurantId = 1L;
        Long menuItemId = 999L;
        
        doThrow(new MenuItemNotFoundException(restaurantId, menuItemId))
            .when(restaurantService).deleteMenuItem(restaurantId, menuItemId);
        
        // When & Then
        mockMvc.perform(delete("/restaurants/{restaurantId}/menu-items/{menuItemId}", restaurantId, menuItemId))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("Menu item not found"));
        
        verify(restaurantService).deleteMenuItem(restaurantId, menuItemId);
    }
}
