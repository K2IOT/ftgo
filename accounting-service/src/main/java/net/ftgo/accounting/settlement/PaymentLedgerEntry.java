package net.ftgo.accounting.settlement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_ledger_entries")
public class PaymentLedgerEntry {

    public enum OperationType {
        AUTHORIZE, CAPTURE, VOID, REFUND
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "authorization_id")
    private Long authorizationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false, length = 32)
    private OperationType operationType;

    @Column(name = "request_id", nullable = false, unique = true, length = 191)
    private String requestId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "provider_reference", length = 191)
    private String providerReference;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    protected PaymentLedgerEntry() {
    }

    public PaymentLedgerEntry(Long accountId, Long orderId, Long authorizationId,
                              OperationType operationType, String requestId,
                              BigDecimal amount, String providerReference) {
        if (accountId == null) throw new IllegalArgumentException("Account ID cannot be null");
        if (operationType == null) throw new IllegalArgumentException("Operation type cannot be null");
        if (requestId == null || requestId.isBlank()) throw new IllegalArgumentException("Request ID cannot be blank");
        if (amount == null || amount.signum() <= 0) throw new IllegalArgumentException("Amount must be positive");
        this.accountId = accountId;
        this.orderId = orderId;
        this.authorizationId = authorizationId;
        this.operationType = operationType;
        this.requestId = requestId;
        this.amount = amount;
        this.currency = "VND";
        this.providerReference = providerReference;
        this.occurredAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getAccountId() { return accountId; }
    public Long getOrderId() { return orderId; }
    public Long getAuthorizationId() { return authorizationId; }
    public OperationType getOperationType() { return operationType; }
    public String getRequestId() { return requestId; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getProviderReference() { return providerReference; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
}
