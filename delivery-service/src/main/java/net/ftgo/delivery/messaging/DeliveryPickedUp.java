package net.ftgo.delivery.messaging;

import java.time.LocalDateTime;

/**
 * Domain event published when a courier picks up an order.
 */
public class DeliveryPickedUp {
    
    private Long deliveryId;
    private Long orderId;
    private Long courierId;
    private LocalDateTime pickupTime;
    
    /**
     * Default constructor for JSON deserialization.
     */
    public DeliveryPickedUp() {
    }
    
    public DeliveryPickedUp(Long deliveryId, Long orderId, Long courierId, LocalDateTime pickupTime) {
        this.deliveryId = deliveryId;
        this.orderId = orderId;
        this.courierId = courierId;
        this.pickupTime = pickupTime;
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
    
    public LocalDateTime getPickupTime() {
        return pickupTime;
    }
    
    public void setPickupTime(LocalDateTime pickupTime) {
        this.pickupTime = pickupTime;
    }
}
