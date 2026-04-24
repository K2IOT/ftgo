package net.ftgo.gateway.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Fallback controller for circuit breaker responses.
 * Provides graceful degradation when downstream services are unavailable.
 */
@RestController
@RequestMapping("/fallback")
public class FallbackController {
    
    @GetMapping("/orders")
    @PostMapping("/orders")
    public ResponseEntity<Map<String, String>> orderServiceFallback() {
        return ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(Map.of(
                "error", "service_unavailable",
                "message", "Order service is temporarily unavailable. Please try again later.",
                "service", "order-service"
            ));
    }
    
    @GetMapping("/consumers")
    @PostMapping("/consumers")
    public ResponseEntity<Map<String, String>> consumerServiceFallback() {
        return ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(Map.of(
                "error", "service_unavailable",
                "message", "Consumer service is temporarily unavailable. Please try again later.",
                "service", "consumer-service"
            ));
    }
    
    @GetMapping("/restaurants")
    @PostMapping("/restaurants")
    public ResponseEntity<Map<String, String>> restaurantServiceFallback() {
        return ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(Map.of(
                "error", "service_unavailable",
                "message", "Restaurant service is temporarily unavailable. Please try again later.",
                "service", "restaurant-service"
            ));
    }
    
    @GetMapping("/tickets")
    @PostMapping("/tickets")
    public ResponseEntity<Map<String, String>> kitchenServiceFallback() {
        return ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(Map.of(
                "error", "service_unavailable",
                "message", "Kitchen service is temporarily unavailable. Please try again later.",
                "service", "kitchen-service"
            ));
    }
    
    @GetMapping("/deliveries")
    @PostMapping("/deliveries")
    public ResponseEntity<Map<String, String>> deliveryServiceFallback() {
        return ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(Map.of(
                "error", "service_unavailable",
                "message", "Delivery service is temporarily unavailable. Please try again later.",
                "service", "delivery-service"
            ));
    }
    
    @GetMapping("/order-history")
    public ResponseEntity<Map<String, String>> orderHistoryServiceFallback() {
        return ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(Map.of(
                "error", "service_unavailable",
                "message", "Order history service is temporarily unavailable. Please try again later.",
                "service", "order-history-service"
            ));
    }
}
