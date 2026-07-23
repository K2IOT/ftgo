package net.ftgo.consumer.domain;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import net.ftgo.common.Money;

import java.time.LocalDateTime;

@Entity
@Table(name = "credit_reservations")
public class CreditReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "consumer_id", nullable = false)
    private Long consumerId;

    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "amount", nullable = false, precision = 19, scale = 2))
    private Money amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CreditReservationStatus status;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected CreditReservation() {
    }

    public CreditReservation(Long consumerId, Long orderId, Money amount) {
        if (consumerId == null || orderId == null) {
            throw new IllegalArgumentException("Consumer ID and order ID are required");
        }
        if (amount == null || amount.isZero()) {
            throw new IllegalArgumentException("Reservation amount must be positive");
        }
        this.consumerId = consumerId;
        this.orderId = orderId;
        this.amount = amount;
        this.status = CreditReservationStatus.RESERVED;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void commit() {
        if (status == CreditReservationStatus.COMMITTED) {
            return;
        }
        if (status != CreditReservationStatus.RESERVED) {
            throw new IllegalStateException("Cannot commit reservation in state " + status);
        }
        status = CreditReservationStatus.COMMITTED;
        updatedAt = LocalDateTime.now();
    }

    public void release() {
        if (status == CreditReservationStatus.RELEASED) {
            return;
        }
        status = CreditReservationStatus.RELEASED;
        updatedAt = LocalDateTime.now();
    }

    public boolean matches(Long expectedConsumerId, Money expectedAmount) {
        return consumerId.equals(expectedConsumerId) && amount.equals(expectedAmount);
    }

    public Long getId() { return id; }
    public Long getConsumerId() { return consumerId; }
    public Long getOrderId() { return orderId; }
    public Money getAmount() { return amount; }
    public CreditReservationStatus getStatus() { return status; }
    public Long getVersion() { return version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    @PrePersist
    protected void onCreate() {
        if (status == null) status = CreditReservationStatus.RESERVED;
        if (version == null) version = 0L;
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
