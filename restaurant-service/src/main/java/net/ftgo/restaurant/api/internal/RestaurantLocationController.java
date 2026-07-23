package net.ftgo.restaurant.api.internal;

import net.ftgo.restaurant.domain.Restaurant;
import net.ftgo.restaurant.service.RestaurantNotFoundException;
import net.ftgo.restaurant.service.RestaurantService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/restaurants")
public class RestaurantLocationController {

    private final RestaurantService restaurantService;

    public RestaurantLocationController(RestaurantService restaurantService) {
        this.restaurantService = restaurantService;
    }

    @GetMapping("/{restaurantId}/pickup-address")
    public ResponseEntity<RestaurantPickupAddressResponse> getPickupAddress(
            @PathVariable Long restaurantId) {
        Restaurant restaurant = restaurantService.findRestaurant(restaurantId);
        return ResponseEntity.ok(new RestaurantPickupAddressResponse(
                restaurantId,
                restaurant.getAddress()));
    }

    @ExceptionHandler(RestaurantNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleRestaurantNotFound(
            RestaurantNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                exception.getMessage());
        problem.setTitle("Restaurant not found");
        problem.setProperty("code", "RESTAURANT_NOT_FOUND");

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
