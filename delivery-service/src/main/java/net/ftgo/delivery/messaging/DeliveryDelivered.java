package net.ftgo.delivery.messaging;

import java.time.LocalDateTime;

/**
 * Domain event published when an order is delivered to the consumer.
 */
public class DeliveryDelivered {
    
    private Long deliveryId;
    private Long orderId;
    private Long courierId;
    private LocalDateTime deliveryTime;
    
    /**
     * Default constructor for JSON deserialization.
     */
    public DeliveryDelivered() {
    }
    
    public DeliveryDelivered(Long deliveryId, Long orderId, Long courierId, LocalDateTime deliveryTime) {
        this.deliveryId = deliveryId;
        this.orderId = orderId;
        this.courierId = courierId;
        this.deliveryTime = deliveryTime;
    }
    
    public Long getDeliveryId() {
        return deliveryId;
    }
    
    public void setDeliveryId(Long deliveryId) {
        this.deliveryId = deliveryId;
    }
    
    public Long getOrderId() {
        return orderId;
    }
    
    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }
    
    public Long getCourierId() {
        return courierId;
    }
    
    public void setCourierId(Long courierId) {
        this.courierId = courierId;
    }
    
    public LocalDateTime getDeliveryTime() {
        return deliveryTime;
    }
    
    public void setDeliveryTime(LocalDateTime deliveryTime) {
        this.deliveryTime = deliveryTime;
    }
}
