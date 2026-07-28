package net.ftgo.restaurant.api;

import jakarta.servlet.http.HttpServletRequest;
import net.ftgo.common.web.FtgoProblemDetail;
import net.ftgo.common.web.FtgoProblemResponses;
import net.ftgo.restaurant.service.MenuItemNotFoundException;
import net.ftgo.restaurant.service.RestaurantNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = RestaurantController.class)
public final class RestaurantApiExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(RestaurantApiExceptionHandler.class);

    @ExceptionHandler(RestaurantNotFoundException.class)
    ResponseEntity<FtgoProblemDetail> restaurantNotFound(
        RestaurantNotFoundException error,
        HttpServletRequest request
    ) {
        logger.warn("Restaurant not found: {}", error.getRestaurantId());
        return FtgoProblemResponses.response(
            HttpStatus.NOT_FOUND,
            "restaurant-not-found",
            "Restaurant not found",
            "Restaurant not found",
            "RESTAURANT_NOT_FOUND",
            request
        );
    }

    @ExceptionHandler(MenuItemNotFoundException.class)
    ResponseEntity<FtgoProblemDetail> menuItemNotFound(
        MenuItemNotFoundException error,
        HttpServletRequest request
    ) {
        logger.warn(
            "Menu item not found: restaurant={}, menuItem={}",
            error.getRestaurantId(),
            error.getMenuItemId()
        );
        return FtgoProblemResponses.response(
            HttpStatus.NOT_FOUND,
            "menu-item-not-found",
            "Menu item not found",
            "Menu item not found",
            "MENU_ITEM_NOT_FOUND",
            request
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<FtgoProblemDetail> invalidRequest(
        IllegalArgumentException error,
        HttpServletRequest request
    ) {
        logger.warn("Invalid restaurant request: {}", error.getMessage());
        return FtgoProblemResponses.response(
            HttpStatus.BAD_REQUEST,
            "invalid-request",
            "Invalid request",
            safeDetail(error, "Invalid restaurant request"),
            "INVALID_REQUEST",
            request
        );
    }

    private String safeDetail(IllegalArgumentException error, String fallback) {
        String detail = error.getMessage();
        return detail == null || detail.isBlank() ? fallback : detail;
    }
}
