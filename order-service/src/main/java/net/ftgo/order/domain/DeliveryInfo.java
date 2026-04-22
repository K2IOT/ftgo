package net.ftgo.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotNull;
import net.ftgo.common.Address;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Value object representing delivery information for an order.
 * 
 * Contains the delivery address and scheduled delivery time.
 */
@Embeddable
public class DeliveryInfo {
    
    @NotNull(message = "Delivery address is required")
    @Column(name = "delivery_address", nullable = false, length = 500)
    private String deliveryAddress;
    
    @NotNull(message = "Delivery time is required")
    @Column(name = "delivery_time", nullable = false)
    private LocalDateTime deliveryTime;
    
    /**
     * Default constructor for JPA.
     */
    protected DeliveryInfo() {
    }
    
    /**
     * Creates a new DeliveryInfo.
     * 
     * @param deliveryAddress the delivery address
     * @param deliveryTime the scheduled delivery time
     * @throws IllegalArgumentException if any parameter is invalid
     */
    public DeliveryInfo(Address deliveryAddress, LocalDateTime deliveryTime) {
        validateDeliveryAddress(deliveryAddress);
        validateDeliveryTime(deliveryTime);
        
        this.deliveryAddress = deliveryAddress.getFullAddress();
        this.deliveryTime = deliveryTime;
    }
    
    /**
     * Creates a new DeliveryInfo with a string address.
     * 
     * @param deliveryAddress the delivery address as a string
     * @param deliveryTime the scheduled delivery time
     * @throws IllegalArgumentException if any parameter is invalid
     */
    public DeliveryInfo(String deliveryAddress, LocalDateTime deliveryTime) {
        validateDeliveryAddressString(deliveryAddress);
        validateDeliveryTime(deliveryTime);
        
        this.deliveryAddress = deliveryAddress;
        this.deliveryTime = deliveryTime;
    }
    
    /**
     * Validates that the delivery address is not null.
     * 
     * @param deliveryAddress the address to validate
     * @throws IllegalArgumentException if address is null
     */
    private void validateDeliveryAddress(Address deliveryAddress) {
        if (deliveryAddress == null) {
            throw new IllegalArgumentException("Delivery address cannot be null");
        }
    }
    
    /**
     * Validates that the delivery address string is not null or blank.
     * 
     * @param deliveryAddress the address string to validate
     * @throws IllegalArgumentException if address is null or blank
     */
    private void validateDeliveryAddressString(String deliveryAddress) {
        if (deliveryAddress == null || deliveryAddress.isBlank()) {
            throw new IllegalArgumentException("Delivery address cannot be null or blank");
        }
    }
    
    /**
     * Validates that the delivery time is not null and is in the future.
     * 
     * @param deliveryTime the delivery time to validate
     * @throws IllegalArgumentException if delivery time is null or in the past
     */
    private void validateDeliveryTime(LocalDateTime deliveryTime) {
        if (deliveryTime == null) {
            throw new IllegalArgumentException("Delivery time cannot be null");
        }
        if (deliveryTime.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Delivery time must be in the future");
        }
    }
    
    // Getters
    
    public String getDeliveryAddress() {
        return deliveryAddress;
    }
    
    public LocalDateTime getDeliveryTime() {
        return deliveryTime;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DeliveryInfo that = (DeliveryInfo) o;
        return Objects.equals(deliveryAddress, that.deliveryAddress) &&
                Objects.equals(deliveryTime, that.deliveryTime);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(deliveryAddress, deliveryTime);
    }
    
    @Override
    public String toString() {
        return String.format("DeliveryInfo{address='%s', time=%s}", deliveryAddress, deliveryTime);
    }
}
