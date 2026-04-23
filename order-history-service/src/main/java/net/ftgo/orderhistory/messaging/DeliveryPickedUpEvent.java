package net.ftgo.orderhistory.messaging;

/**
 * Domain event published when a delivery is picked up by courier.
 * 
 * Published by Delivery Service to net.ftgo.deliveryservice.domain.Delivery topic.
 */
public class DeliveryPickedUpEvent {
    
    private Long deliveryId;
    private Long orderId;
    
    /**
     * Default constructor for JSON deserialization.
     */
    public DeliveryPickedUpEvent() {
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
}
