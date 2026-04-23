package net.ftgo.delivery.messaging;

import net.ftgo.common.Address;

import java.time.LocalDateTime;

/**
 * Domain event received when an order is approved.
 * Triggers creation of a delivery record.
 */
public class OrderApproved {
    
    private Long orderId;
    private Long restaurantId;
    private Address pickupAddress;
    private Address deliveryAddress;
    private LocalDateTime scheduledTime;
    
    /**
     * Default constructor for JSON deserialization.
     */
    public OrderApproved() {
    }
    
    public OrderApproved(Long orderId, Long restaurantId, Address pickupAddress, 
                        Address deliveryAddress, LocalDateTime scheduledTime) {
        this.orderId = orderId;
        this.restaurantId = restaurantId;
        this.pickupAddress = pickupAddress;
        this.deliveryAddress = deliveryAddress;
        this.scheduledTime = scheduledTime;
    }
    
    public Long getOrderId() {
        return orderId;
    }
    
    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }
    
    public Long getRestaurantId() {
        return restaurantId;
    }
    
    public void setRestaurantId(Long restaurantId) {
        this.restaurantId = restaurantId;
    }
    
    public Address getPickupAddress() {
        return pickupAddress;
    }
    
    public void setPickupAddress(Address pickupAddress) {
        this.pickupAddress = pickupAddress;
    }
    
    public Address getDeliveryAddress() {
        return deliveryAddress;
    }
    
    public void setDeliveryAddress(Address deliveryAddress) {
        this.deliveryAddress = deliveryAddress;
    }
    
    public LocalDateTime getScheduledTime() {
        return scheduledTime;
    }
    
    public void setScheduledTime(LocalDateTime scheduledTime) {
        this.scheduledTime = scheduledTime;
    }
}
