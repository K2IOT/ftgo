package net.ftgo.delivery.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import net.ftgo.common.Address;

import java.time.LocalDateTime;

/**
 * Delivery aggregate representing a delivery order.
 * 
 * Implements state machine transitions for delivery lifecycle:
 * PENDING → ASSIGNED → PICKED_UP → DELIVERED
 * 
 * Enforces valid state transitions and temporal ordering (pickupTime < deliveryTime).
 */
@Entity
@Table(name = "deliveries")
public class Delivery {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @NotNull(message = "Order ID is required")
    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;
    
    @Column(name = "courier_id")
    private Long courierId;
    
    @NotNull(message = "Pickup address is required")
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "street", column = @Column(name = "pickup_street")),
        @AttributeOverride(name = "city", column = @Column(name = "pickup_city")),
        @AttributeOverride(name = "state", column = @Column(name = "pickup_state")),
        @AttributeOverride(name = "zipCode", column = @Column(name = "pickup_zip_code"))
    })
    private Address pickupAddress;
    
    @NotNull(message = "Delivery address is required")
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "street", column = @Column(name = "delivery_street")),
        @AttributeOverride(name = "city", column = @Column(name = "delivery_city")),
        @AttributeOverride(name = "state", column = @Column(name = "delivery_state")),
        @AttributeOverride(name = "zipCode", column = @Column(name = "delivery_zip_code"))
    })
    private Address deliveryAddress;
    
    @NotNull(message = "Scheduled time is required")
    @Column(name = "scheduled_time", nullable = false)
    private LocalDateTime scheduledTime;
    
    @Column(name = "pickup_time")
    private LocalDateTime pickupTime;
    
    @Column(name = "delivery_time")
    private LocalDateTime deliveryTime;
    
    @NotNull(message = "Status is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private DeliveryStatus status;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    /**
     * Default constructor for JPA.
     */
    protected Delivery() {
    }
    
    /**
     * Creates a new Delivery in PENDING state.
     * 
     * @param orderId the order ID
     * @param pickupAddress the restaurant pickup address
     * @param deliveryAddress the consumer delivery address
     * @param scheduledTime the scheduled delivery time
     * @throws IllegalArgumentException if any parameter is invalid
     */
    public Delivery(Long orderId, Address pickupAddress, Address deliveryAddress, 
                   LocalDateTime scheduledTime) {
        validateOrderId(orderId);
        validatePickupAddress(pickupAddress);
        validateDeliveryAddress(deliveryAddress);
        validateScheduledTime(scheduledTime);
        
        this.orderId = orderId;
        this.pickupAddress = pickupAddress;
        this.deliveryAddress = deliveryAddress;
        this.scheduledTime = scheduledTime;
        this.status = DeliveryStatus.PENDING;
        this.createdAt = LocalDateTime.now();
    }
    
    private void validateOrderId(Long orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException("Order ID cannot be null");
        }
    }
    
    private void validatePickupAddress(Address pickupAddress) {
        if (pickupAddress == null) {
            throw new IllegalArgumentException("Pickup address cannot be null");
        }
    }
    
    private void validateDeliveryAddress(Address deliveryAddress) {
        if (deliveryAddress == null) {
            throw new IllegalArgumentException("Delivery address cannot be null");
        }
    }
    
    private void validateScheduledTime(LocalDateTime scheduledTime) {
        if (scheduledTime == null) {
            throw new IllegalArgumentException("Scheduled time cannot be null");
        }
    }
    
    /**
     * Assigns a courier to the delivery.
     * Transitions from PENDING to ASSIGNED.
     * 
     * @param courierId the courier ID
     * @throws IllegalStateException if current status is not PENDING
     * @throws IllegalArgumentException if courierId is null
     */
    public void assignCourier(Long courierId) {
        if (status != DeliveryStatus.PENDING) {
            throw new IllegalStateException(
                String.format("Cannot assign courier in status %s. Expected PENDING.", status)
            );
        }
        if (courierId == null) {
            throw new IllegalArgumentException("Courier ID cannot be null");
        }
        
        this.courierId = courierId;
        this.status = DeliveryStatus.ASSIGNED;
    }
    
    /**
     * Marks the delivery as picked up by the courier.
     * Transitions from ASSIGNED to PICKED_UP.
     * 
     * @throws IllegalStateException if current status is not ASSIGNED
     */
    public void pickup() {
        if (status != DeliveryStatus.ASSIGNED) {
            throw new IllegalStateException(
                String.format("Cannot pickup delivery in status %s. Expected ASSIGNED.", status)
            );
        }
        
        this.pickupTime = LocalDateTime.now();
        this.status = DeliveryStatus.PICKED_UP;
    }
    
    /**
     * Marks the delivery as delivered to the consumer.
     * Transitions from PICKED_UP to DELIVERED.
     * 
     * @throws IllegalStateException if current status is not PICKED_UP
     * @throws IllegalStateException if deliveryTime would be before pickupTime
     */
    public void deliver() {
        if (status != DeliveryStatus.PICKED_UP) {
            throw new IllegalStateException(
                String.format("Cannot deliver in status %s. Expected PICKED_UP.", status)
            );
        }
        
        LocalDateTime now = LocalDateTime.now();
        
        // Enforce temporal ordering: pickupTime < deliveryTime
        if (pickupTime != null && now.isBefore(pickupTime)) {
            throw new IllegalStateException(
                "Delivery time cannot be before pickup time (temporal ordering violation)"
            );
        }
        
        this.deliveryTime = now;
        this.status = DeliveryStatus.DELIVERED;
    }
    
    /**
     * Calculates estimated delivery time based on distance.
     * Simple calculation: 30 minutes base + 5 minutes per 10 miles.
     * 
     * @return estimated delivery time
     */
    public LocalDateTime calculateEstimatedDeliveryTime() {
        // Simple distance estimation based on zip code difference
        // In a real system, this would use a mapping/routing service
        double estimatedDistanceMiles = estimateDistance(pickupAddress, deliveryAddress);
        
        // Base time: 30 minutes + 5 minutes per 10 miles
        long estimatedMinutes = 30 + (long) (estimatedDistanceMiles / 10.0 * 5);
        
        return scheduledTime.plusMinutes(estimatedMinutes);
    }
    
    /**
     * Estimates distance between two addresses in miles.
     * Simplified calculation for demonstration purposes.
     * 
     * @param from pickup address
     * @param to delivery address
     * @return estimated distance in miles
     */
    private double estimateDistance(Address from, Address to) {
        // Simple heuristic: same city = 5 miles, different city = 20 miles
        if (from.getCity().equalsIgnoreCase(to.getCity())) {
            return 5.0;
        } else {
            return 20.0;
        }
    }
    
    // Getters
    
    public Long getId() {
        return id;
    }
    
    public Long getOrderId() {
        return orderId;
    }
    
    public Long getCourierId() {
        return courierId;
    }
    
    public Address getPickupAddress() {
        return pickupAddress;
    }
    
    public Address getDeliveryAddress() {
        return deliveryAddress;
    }
    
    public LocalDateTime getScheduledTime() {
        return scheduledTime;
    }
    
    public LocalDateTime getPickupTime() {
        return pickupTime;
    }
    
    public LocalDateTime getDeliveryTime() {
        return deliveryTime;
    }
    
    public DeliveryStatus getStatus() {
        return status;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
    
    @Override
    public String toString() {
        return String.format("Delivery[id=%d, orderId=%d, courierId=%s, status=%s]",
            id, orderId, courierId, status);
    }
}
