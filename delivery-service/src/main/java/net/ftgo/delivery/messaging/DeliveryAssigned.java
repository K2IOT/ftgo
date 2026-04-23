package net.ftgo.delivery.messaging;

/**
 * Domain event published when a courier is assigned to a delivery.
 */
public class DeliveryAssigned {
    
    private Long deliveryId;
    private Long orderId;
    private Long courierId;
    
    /**
     * Default constructor for JSON deserialization.
     */
    public DeliveryAssigned() {
    }
    
    public DeliveryAssigned(Long deliveryId, Long orderId, Long courierId) {
        this.deliveryId = deliveryId;
        this.orderId = orderId;
        this.courierId = courierId;
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
}
