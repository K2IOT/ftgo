package net.ftgo.order.domain.events;

/**
 * Domain event published when an order is rejected.
 * 
 * Published by CreateOrderSaga when the saga fails before the pivot point.
 * Consumed by:
 * - Order History Service: Updates order status to REJECTED
 */
public class OrderRejected {
    
    private Long orderId;
    private Long consumerId;
    private Long restaurantId;
    private String reason;
    
    /**
     * Default constructor for serialization.
     */
    public OrderRejected() {
    }
    
    /**
     * Creates an OrderRejected event.
     * 
     * @param orderId the order ID
     * @param consumerId the consumer ID
     * @param restaurantId the restaurant ID
     * @param reason the rejection reason
     */
    public OrderRejected(Long orderId, Long consumerId, Long restaurantId, String reason) {
        this.orderId = orderId;
        this.consumerId = consumerId;
        this.restaurantId = restaurantId;
        this.reason = reason;
    }
    
    public Long getOrderId() {
        return orderId;
    }
    
    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }
    
    public Long getConsumerId() {
        return consumerId;
    }
    
    public void setConsumerId(Long consumerId) {
        this.consumerId = consumerId;
    }
    
    public Long getRestaurantId() {
        return restaurantId;
    }
    
    public void setRestaurantId(Long restaurantId) {
        this.restaurantId = restaurantId;
    }
    
    public String getReason() {
        return reason;
    }
    
    public void setReason(String reason) {
        this.reason = reason;
    }
}
