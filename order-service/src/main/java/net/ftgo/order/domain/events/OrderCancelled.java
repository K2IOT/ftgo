package net.ftgo.order.domain.events;

/**
 * Domain event published when an order is successfully cancelled.
 * 
 * This event is published by CancelOrderSaga when the cancellation completes:
 * - Payment authorization has been reversed
 * - Kitchen ticket has been cancelled
 * - Order state has transitioned to CANCELLED
 * 
 * Consumers of this event:
 * - Order History Service: Updates read model to show order as cancelled
 * - Delivery Service: Cancels any pending delivery assignments
 * - Analytics Service: Records cancellation metrics
 */
public class OrderCancelled {
    
    private Long orderId;
    private Long consumerId;
    private Long restaurantId;
    
    /**
     * Default constructor for serialization.
     */
    public OrderCancelled() {
    }
    
    /**
     * Creates an OrderCancelled event.
     * 
     * @param orderId the cancelled order ID
     * @param consumerId the consumer who cancelled the order
     * @param restaurantId the restaurant for the cancelled order
     */
    public OrderCancelled(Long orderId, Long consumerId, Long restaurantId) {
        this.orderId = orderId;
        this.consumerId = consumerId;
        this.restaurantId = restaurantId;
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
    
    @Override
    public String toString() {
        return String.format("OrderCancelled{orderId=%d, consumerId=%d, restaurantId=%d}",
            orderId, consumerId, restaurantId);
    }
}
