package net.ftgo.accounting.settlement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import net.ftgo.common.Money;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "simulated_provider_payments")
public class SimulatedProviderPayment {

    @Id
    @Column(name = "authorization_id", nullable = false)
    private Long authorizationId;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "authorized_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal authorizedAmount;

    @Column(name = "captured_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal capturedAmount;

    @Column(name = "refunded_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal refundedAmount;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 32)
    private ProviderSettlementStatus status;

    @Column(name = "provider_reference", nullable = false, length = 191)
    private String providerReference;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected SimulatedProviderPayment() {
    }

    public SimulatedProviderPayment(
        Long authorizationId,
        Long orderId,
        Money authorizedAmount,
        String providerReference
    ) {
        if (authorizationId == null) throw new IllegalArgumentException("Authorization ID cannot be null");
        if (authorizedAmount == null || authorizedAmount.isZero()) {
            throw new IllegalArgumentException("Authorized amount must be positive");
        }
        if (providerReference == null || providerReference.isBlank()) {
            throw new IllegalArgumentException("Provider reference cannot be blank");
        }
        this.authorizationId = authorizationId;
        this.orderId = orderId;
        this.authorizedAmount = authorizedAmount.getAmount();
        this.capturedAmount = BigDecimal.ZERO.setScale(2);
        this.refundedAmount = BigDecimal.ZERO.setScale(2);
        this.status = ProviderSettlementStatus.AUTHORIZED;
        this.providerReference = providerReference;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = createdAt;
    }

    public void capture() {
        if (status != ProviderSettlementStatus.AUTHORIZED) {
            throw new IllegalStateException("Cannot capture provider payment in state " + status);
        }
        capturedAmount = authorizedAmount;
        status = ProviderSettlementStatus.CAPTURED;
        updatedAt = LocalDateTime.now();
    }

    public void voidAuthorization() {
        if (status != ProviderSettlementStatus.AUTHORIZED) {
            throw new IllegalStateException("Cannot void provider payment in state " + status);
        }
        status = ProviderSettlementStatus.VOIDED;
        updatedAt = LocalDateTime.now();
    }

    public void refund(Money amount) {
        if (amount == null || amount.isZero()) {
            throw new IllegalArgumentException("Refund amount must be positive");
        }
        BigDecimal next = refundedAmount.add(amount.getAmount());
        if (next.compareTo(capturedAmount) > 0) {
            throw new IllegalArgumentException("Refund total exceeds captured amount");
        }
        if (status != ProviderSettlementStatus.CAPTURED
            && status != ProviderSettlementStatus.PARTIALLY_REFUNDED) {
            throw new IllegalStateException("Cannot refund provider payment in state " + status);
        }
        refundedAmount = next;
        status = refundedAmount.compareTo(capturedAmount) == 0
            ? ProviderSettlementStatus.REFUNDED
            : ProviderSettlementStatus.PARTIALLY_REFUNDED;
        updatedAt = LocalDateTime.now();
    }

    public void synchronize(SettlementTarget target, String providerReference) {
        if (!authorizationId.equals(target.authorizationId())) {
            throw new IllegalArgumentException("Settlement target belongs to another authorization");
        }
        this.orderId = target.orderId();
        this.authorizedAmount = target.authorizedAmount().getAmount();
        this.capturedAmount = target.capturedAmount().getAmount();
        this.refundedAmount = target.refundedAmount().getAmount();
        this.status = target.status();
        this.providerReference = providerReference;
        this.updatedAt = LocalDateTime.now();
    }

    public boolean matchesAuthorization(Long expectedOrderId, Money expectedAmount) {
        return java.util.Objects.equals(orderId, expectedOrderId)
            && authorizedAmount.compareTo(expectedAmount.getAmount()) == 0;
    }

    public ProviderSettlementSnapshot snapshot() {
        return new ProviderSettlementSnapshot(
            authorizationId,
            orderId,
            new Money(authorizedAmount),
            new Money(capturedAmount),
            new Money(refundedAmount),
            status,
            providerReference
        );
    }

    public Long getAuthorizationId() { return authorizationId; }
    public Long getOrderId() { return orderId; }
    public BigDecimal getAuthorizedAmount() { return authorizedAmount; }
    public BigDecimal getCapturedAmount() { return capturedAmount; }
    public BigDecimal getRefundedAmount() { return refundedAmount; }
    public ProviderSettlementStatus getStatus() { return status; }
    public String getProviderReference() { return providerReference; }
    public Long getVersion() { return version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    @PrePersist
    protected void onCreate() {
        if (capturedAmount == null) capturedAmount = BigDecimal.ZERO.setScale(2);
        if (refundedAmount == null) refundedAmount = BigDecimal.ZERO.setScale(2);
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (updatedAt == null) updatedAt = createdAt;
        if (version == null) version = 0L;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}