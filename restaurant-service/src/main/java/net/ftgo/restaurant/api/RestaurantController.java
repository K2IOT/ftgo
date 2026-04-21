package net.ftgo.restaurant.api;

import jakarta.validation.Valid;
import net.ftgo.restaurant.domain.MenuItem;
import net.ftgo.restaurant.domain.Restaurant;
import net.ftgo.restaurant.service.MenuItemNotFoundException;
import net.ftgo.restaurant.service.RestaurantNotFoundException;
import net.ftgo.restaurant.service.RestaurantService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST controller for restaurant and menu management.
 */
@RestController
@RequestMapping("/restaurants")
public class RestaurantController {
    
    private static final Logger logger = LoggerFactory.getLogger(RestaurantController.class);
    
    private final RestaurantService restaurantService;
    
    public RestaurantController(RestaurantService restaurantService) {
        this.restaurantService = restaurantService;
    }
    
    /**
     * Creates a new restaurant.
     * 
     * @param request the restaurant creation request
     * @return the created restaurant
     */
    @PostMapping
    public ResponseEntity<RestaurantResponse> createRestaurant(
            @Valid @RequestBody CreateRestaurantRequest request) {
        logger.info("POST /restaurants - Creating restaurant: {}", request.getName());
        
        Restaurant restaurant = new Restaurant(
            request.getName(),
            request.getAddress(),
            request.getOpeningHours()
        );
        
        Restaurant created = restaurantService.createRestaurant(restaurant);
        
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(new RestaurantResponse(created));
    }
    
    /**
     * Gets a restaurant by ID.
     * 
     * @param restaurantId the restaurant ID
     * @return the restaurant
     */
    @GetMapping("/{restaurantId}")
    public ResponseEntity<RestaurantResponse> getRestaurant(
            @PathVariable Long restaurantId) {
        logger.info("GET /restaurants/{} - Getting restaurant", restaurantId);
        
        Restaurant restaurant = restaurantService.findRestaurant(restaurantId);
        
        return ResponseEntity.ok(new RestaurantResponse(restaurant));
    }
    
    /**
     * Creates a new menu item for a restaurant.
     * 
     * @param restaurantId the restaurant ID
     * @param request the menu item creation request
     * @return the created menu item
     */
    @PostMapping("/{restaurantId}/menu-items")
    public ResponseEntity<MenuItemResponse> createMenuItem(
            @PathVariable Long restaurantId,
            @Valid @RequestBody CreateMenuItemRequest request) {
        logger.info("POST /restaurants/{}/menu-items - Creating menu item: {}", 
            restaurantId, request.getName());
        
        MenuItem menuItem = new MenuItem(
            restaurantId,
            request.getName(),
            request.getDescription(),
            request.getPrice()
        );
        
        MenuItem created = restaurantService.createMenuItem(restaurantId, menuItem);
        
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(new MenuItemResponse(created));
    }
    
    /**
     * Gets all menu items for a restaurant.
     * 
     * @param restaurantId the restaurant ID
     * @return list of menu items
     */
    @GetMapping("/{restaurantId}/menu-items")
    public ResponseEntity<List<MenuItemResponse>> getMenuItems(
            @PathVariable Long restaurantId) {
        logger.info("GET /restaurants/{}/menu-items - Getting menu items", restaurantId);
        
        List<MenuItem> menuItems = restaurantService.getMenuItems(restaurantId);
        
        List<MenuItemResponse> responses = menuItems.stream()
            .map(MenuItemResponse::new)
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(responses);
    }
    
    /**
     * Updates a menu item.
     * 
     * @param restaurantId the restaurant ID
     * @param menuItemId the menu item ID
     * @param request the update request
     * @return the updated menu item
     */
    @PutMapping("/{restaurantId}/menu-items/{menuItemId}")
    public ResponseEntity<MenuItemResponse> updateMenuItem(
            @PathVariable Long restaurantId,
            @PathVariable Long menuItemId,
            @Valid @RequestBody UpdateMenuItemRequest request) {
        logger.info("PUT /restaurants/{}/menu-items/{} - Updating menu item", 
            restaurantId, menuItemId);
        
        MenuItem updated = restaurantService.updateMenuItem(
            restaurantId,
            menuItemId,
            request.getName(),
            request.getDescription(),
            request.getPrice(),
            request.getAvailable()
        );
        
        return ResponseEntity.ok(new MenuItemResponse(updated));
    }
    
    /**
     * Deletes a menu item.
     * 
     * @param restaurantId the restaurant ID
     * @param menuItemId the menu item ID
     * @return no content
     */
    @DeleteMapping("/{restaurantId}/menu-items/{menuItemId}")
    public ResponseEntity<Void> deleteMenuItem(
            @PathVariable Long restaurantId,
            @PathVariable Long menuItemId) {
        logger.info("DELETE /restaurants/{}/menu-items/{} - Deleting menu item", 
            restaurantId, menuItemId);
        
        restaurantService.deleteMenuItem(restaurantId, menuItemId);
        
        return ResponseEntity.noContent().build();
    }
    
    /**
     * Exception handler for RestaurantNotFoundException.
     */
    @ExceptionHandler(RestaurantNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleRestaurantNotFound(RestaurantNotFoundException ex) {
        logger.warn("Restaurant not found: {}", ex.getRestaurantId());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse("Restaurant not found", ex.getMessage()));
    }
    
    /**
     * Exception handler for MenuItemNotFoundException.
     */
    @ExceptionHandler(MenuItemNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleMenuItemNotFound(MenuItemNotFoundException ex) {
        logger.warn("Menu item not found: restaurant={}, menuItem={}", 
            ex.getRestaurantId(), ex.getMenuItemId());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse("Menu item not found", ex.getMessage()));
    }
    
    /**
     * Exception handler for IllegalArgumentException.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        logger.warn("Invalid request: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ErrorResponse("Invalid request", ex.getMessage()));
    }
    
    /**
     * Error response DTO.
     */
    public static class ErrorResponse {
        private String error;
        private String message;
        
        public ErrorResponse(String error, String message) {
            this.error = error;
            this.message = message;
        }
        
        public String getError() {
            return error;
        }
        
        public void setError(String error) {
            this.error = error;
        }
        
        public String getMessage() {
            return message;
        }
        
        public void setMessage(String message) {
            this.message = message;
        }
    }
}
