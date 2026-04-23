package net.ftgo.delivery.api;

import net.ftgo.common.Address;
import net.ftgo.delivery.domain.Delivery;
import net.ftgo.delivery.domain.DeliveryStatus;

import java.time.LocalDateTime;

/**
 * Response DTO for delivery information.
 */
public class DeliveryResponse {
    
    private Long id;
    private Long orderId;
    private Long courierId;
    private Address pickupAddress;
    private Address deliveryAddress;
    private LocalDateTime scheduledTime;
    private LocalDateTime pickupTime;
    private LocalDateTime deliveryTime;
    private LocalDateTime estimatedDeliveryTime;
    private DeliveryStatus status;
    
    public DeliveryResponse() {
    }
    
    public DeliveryResponse(Delivery delivery) {
        this.id = delivery.getId();
        this.orderId = delivery.getOrderId();
        this.courierId = delivery.getCourierId();
        this.pickupAddress = delivery.getPickupAddress();
        this.deliveryAddress = delivery.getDeliveryAddress();
        this.scheduledTime = delivery.getScheduledTime();
        this.pickupTime = delivery.getPickupTime();
        this.deliveryTime = delivery.getDeliveryTime();
        this.estimatedDeliveryTime = delivery.calculateEstimatedDeliveryTime();
        this.status = delivery.getStatus();
    }
    
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
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
    
    public LocalDateTime getPickupTime() {
        return pickupTime;
    }
    
    public void setPickupTime(LocalDateTime pickupTime) {
        this.pickupTime = pickupTime;
    }
    
    public LocalDateTime getDeliveryTime() {
        return deliveryTime;
    }
    
    public void setDeliveryTime(LocalDateTime deliveryTime) {
        this.deliveryTime = deliveryTime;
    }
    
    public LocalDateTime getEstimatedDeliveryTime() {
        return estimatedDeliveryTime;
    }
    
    public void setEstimatedDeliveryTime(LocalDateTime estimatedDeliveryTime) {
        this.estimatedDeliveryTime = estimatedDeliveryTime;
    }
    
    public DeliveryStatus getStatus() {
        return status;
    }
    
    public void setStatus(DeliveryStatus status) {
        this.status = status;
    }
}
