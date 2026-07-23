package net.ftgo.restaurant.api.internal;

import net.ftgo.common.Address;
import net.ftgo.restaurant.domain.Restaurant;
import net.ftgo.restaurant.service.RestaurantNotFoundException;
import net.ftgo.restaurant.service.RestaurantService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RestaurantLocationController.class)
class RestaurantLocationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RestaurantService restaurantService;

    @Test
    void returnsAuthoritativePickupAddress() throws Exception {
        Long restaurantId = 42L;
        Address address = new Address("10 Kitchen Road", "Bangkok", "Bangkok", "10110");
        Restaurant restaurant = new Restaurant("FTGO Kitchen", address, "{}");
        when(restaurantService.findRestaurant(restaurantId)).thenReturn(restaurant);

        mockMvc.perform(get("/internal/restaurants/{restaurantId}/pickup-address", restaurantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restaurantId").value(42))
                .andExpect(jsonPath("$.address.street").value("10 Kitchen Road"))
                .andExpect(jsonPath("$.address.city").value("Bangkok"))
                .andExpect(jsonPath("$.address.state").value("Bangkok"))
                .andExpect(jsonPath("$.address.zipCode").value("10110"));

        verify(restaurantService).findRestaurant(restaurantId);
    }

    @Test
    void returnsRfc9457ProblemWhenRestaurantDoesNotExist() throws Exception {
        Long restaurantId = 999L;
        when(restaurantService.findRestaurant(restaurantId))
                .thenThrow(new RestaurantNotFoundException(restaurantId));

        mockMvc.perform(get("/internal/restaurants/{restaurantId}/pickup-address", restaurantId))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Restaurant not found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Restaurant not found: 999"))
                .andExpect(jsonPath("$.code").value("RESTAURANT_NOT_FOUND"));
    }
}
