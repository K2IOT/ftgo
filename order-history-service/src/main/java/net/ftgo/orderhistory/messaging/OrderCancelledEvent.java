package net.ftgo.orderhistory.messaging;

/**
 * Domain event published when an order is cancelled.
 * 
 * Published by Order Service to net.ftgo.orderservice.domain.Order topic.
 */
public class OrderCancelledEvent {
    
    private Long orderId;
    
    /**
     * Default constructor for JSON deserialization.
     */
    public OrderCancelledEvent() {
    }
    
    public OrderCancelledEvent(Long orderId) {
        this.orderId = orderId;
    }
    
    public Long getOrderId() {
        return orderId;
    }
    
    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }
}
