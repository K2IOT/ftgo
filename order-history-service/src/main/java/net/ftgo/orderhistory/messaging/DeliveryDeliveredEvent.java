package net.ftgo.orderhistory.messaging;

/**
 * Domain event published when a delivery is completed.
 * 
 * Published by Delivery Service to net.ftgo.deliveryservice.domain.Delivery topic.
 */
public class DeliveryDeliveredEvent {
    
    private Long deliveryId;
    private Long orderId;
    
    /**
     * Default constructor for JSON deserialization.
     */
    public DeliveryDeliveredEvent() {
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
