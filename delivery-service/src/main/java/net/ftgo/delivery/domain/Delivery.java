package net.ftgo.delivery.domain;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotNull;
import net.ftgo.common.Address;

import java.time.LocalDateTime;

/** Delivery aggregate and lifecycle state machine. */
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

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Delivery() {
    }

    public Delivery(
        Long orderId,
        Address pickupAddress,
        Address deliveryAddress,
        LocalDateTime scheduledTime
    ) {
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

    private void validateOrderId(Long value) {
        if (value == null) {
            throw new IllegalArgumentException("Order ID cannot be null");
        }
    }

    private void validatePickupAddress(Address value) {
        if (value == null) {
            throw new IllegalArgumentException("Pickup address cannot be null");
        }
    }

    private void validateDeliveryAddress(Address value) {
        if (value == null) {
            throw new IllegalArgumentException("Delivery address cannot be null");
        }
    }

    private void validateScheduledTime(LocalDateTime value) {
        if (value == null) {
            throw new IllegalArgumentException("Scheduled time cannot be null");
        }
    }

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

    public void pickup() {
        if (status != DeliveryStatus.ASSIGNED) {
            throw new IllegalStateException(
                String.format("Cannot pickup delivery in status %s. Expected ASSIGNED.", status)
            );
        }
        this.pickupTime = LocalDateTime.now();
        this.status = DeliveryStatus.PICKED_UP;
    }

    public void deliver() {
        if (status != DeliveryStatus.PICKED_UP) {
            throw new IllegalStateException(
                String.format("Cannot deliver in status %s. Expected PICKED_UP.", status)
            );
        }
        LocalDateTime now = LocalDateTime.now();
        if (pickupTime != null && now.isBefore(pickupTime)) {
            throw new IllegalStateException(
                "Delivery time cannot be before pickup time (temporal ordering violation)"
            );
        }
        this.deliveryTime = now;
        this.status = DeliveryStatus.DELIVERED;
    }

    public LocalDateTime calculateEstimatedDeliveryTime() {
        double estimatedDistanceMiles = estimateDistance(pickupAddress, deliveryAddress);
        long estimatedMinutes = 30 + (long) (estimatedDistanceMiles / 10.0 * 5);
        return scheduledTime.plusMinutes(estimatedMinutes);
    }

    private double estimateDistance(Address from, Address to) {
        return from.getCity().equalsIgnoreCase(to.getCity()) ? 5.0 : 20.0;
    }

    public Long getId() { return id; }
    public Long getOrderId() { return orderId; }
    public Long getCourierId() { return courierId; }
    public Address getPickupAddress() { return pickupAddress; }
    public Address getDeliveryAddress() { return deliveryAddress; }
    public LocalDateTime getScheduledTime() { return scheduledTime; }
    public LocalDateTime getPickupTime() { return pickupTime; }
    public LocalDateTime getDeliveryTime() { return deliveryTime; }
    public DeliveryStatus getStatus() { return status; }
    public Long getVersion() { return version; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    @PrePersist
    protected void onCreate() {
        if (version == null) {
            version = 0L;
        }
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
