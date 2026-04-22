package net.ftgo.order.api;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response DTO for order creation.
 * 
 * Returns the ID of the newly created order.
 */
public class CreateOrderResponse {
    
    @JsonProperty
    private final Long orderId;
    
    public CreateOrderResponse(Long orderId) {
        this.orderId = orderId;
    }
    
    public Long getOrderId() {
        return orderId;
    }
}
