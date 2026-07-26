package net.ftgo.accounting.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import net.ftgo.common.Money;

import java.time.LocalDateTime;

@Entity
@Table(name = "payment_refunds")
public class PaymentRefund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "authorization_id", nullable = false, insertable = false, updatable = false)
    private Long authorizationId;

    @Column(name = "request_id", nullable = false, unique = true, length = 191)
    private String requestId;

    @Column(nullable = false, precision = 19, scale = 2)
    private java.math.BigDecimal amount;

    @Column(length = 255)
    private String reason;

    @Column(name = "provider_reference", length = 191)
    private String providerReference;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected PaymentRefund() {
    }

    public PaymentRefund(String requestId, Money amount, String reason, String providerReference) {
        if (requestId == null || requestId.isBlank()) throw new IllegalArgumentException("Request ID cannot be blank");
        if (amount == null || amount.isZero()) throw new IllegalArgumentException("Refund amount must be positive");
        this.requestId = requestId;
        this.amount = amount.getAmount();
        this.reason = reason;
        this.providerReference = providerReference;
        this.createdAt = LocalDateTime.now();
    }

    public boolean matches(Money expectedAmount) {
        return amount.compareTo(expectedAmount.getAmount()) == 0;
    }

    public Long getId() { return id; }
    public Long getAuthorizationId() { return authorizationId; }
    public String getRequestId() { return requestId; }
    public Money getAmount() { return new Money(amount); }
    public String getReason() { return reason; }
    public String getProviderReference() { return providerReference; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
