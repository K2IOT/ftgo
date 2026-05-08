package net.ftgo.gateway.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fallback controller for circuit breaker responses.
 * Provides graceful degradation when downstream services are unavailable.
 *
 * B4 FIX: Uses @RequestMapping(method = {GET, POST}) instead of dual
 * @GetMapping/@PostMapping annotations to avoid ambiguous handler mappings.
 */
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @RequestMapping(value = "/orders", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<Map<String, Object>> orderServiceFallback() {
        return buildFallbackResponse("order-service", "Order service is temporarily unavailable. Please try again later.");
    }

    @RequestMapping(value = "/consumers", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<Map<String, Object>> consumerServiceFallback() {
        return buildFallbackResponse("consumer-service", "Consumer service is temporarily unavailable. Please try again later.");
    }

    @RequestMapping(value = "/restaurants", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<Map<String, Object>> restaurantServiceFallback() {
        return buildFallbackResponse("restaurant-service", "Restaurant service is temporarily unavailable. Please try again later.");
    }

    @RequestMapping(value = "/tickets", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<Map<String, Object>> kitchenServiceFallback() {
        return buildFallbackResponse("kitchen-service", "Kitchen service is temporarily unavailable. Please try again later.");
    }

    @RequestMapping(value = "/accounts", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<Map<String, Object>> accountingServiceFallback() {
        return buildFallbackResponse("accounting-service", "Accounting service is temporarily unavailable. Please try again later.");
    }

    @RequestMapping(value = "/deliveries", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<Map<String, Object>> deliveryServiceFallback() {
        return buildFallbackResponse("delivery-service", "Delivery service is temporarily unavailable. Please try again later.");
    }

    @RequestMapping(value = "/order-history", method = {RequestMethod.GET})
    public ResponseEntity<Map<String, Object>> orderHistoryServiceFallback() {
        return buildFallbackResponse("order-history-service", "Order history service is temporarily unavailable. Please try again later.");
    }

    /**
     * Build a structured fallback response with timestamp and service identification.
     */
    private ResponseEntity<Map<String, Object>> buildFallbackResponse(String service, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "service_unavailable");
        body.put("message", message);
        body.put("service", service);
        body.put("timestamp", Instant.now().toString());
        body.put("status", HttpStatus.SERVICE_UNAVAILABLE.value());
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(body);
    }
}
