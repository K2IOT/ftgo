package net.ftgo.accounting.settlement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "simulated_provider_operations")
public class SimulatedProviderOperation {

    public enum OperationType {
        AUTHORIZE,
        CAPTURE,
        VOID,
        REFUND,
        SYNCHRONIZE
    }

    public enum Outcome {
        APPROVED,
        DENIED,
        TIMEOUT,
        RETRY_EXHAUSTED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false, unique = true, length = 191)
    private String requestId;

    @Column(name = "authorization_id", nullable = false)
    private Long authorizationId;

    @Column(name = "order_id")
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "operation_type", nullable = false, length = 32)
    private OperationType operationType;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 32)
    private Outcome outcome;

    @Column(name = "provider_reference", length = 191)
    private String providerReference;

    @Column(length = 255)
    private String reason;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected SimulatedProviderOperation() {
    }

    public SimulatedProviderOperation(
        String requestId,
        Long authorizationId,
        Long orderId,
        OperationType operationType,
        BigDecimal amount
    ) {
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("Request ID cannot be blank");
        }
        if (authorizationId == null) {
            throw new IllegalArgumentException("Authorization ID cannot be null");
        }
        if (operationType == null) {
            throw new IllegalArgumentException("Operation type cannot be null");
        }
        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("Operation amount cannot be negative");
        }
        this.requestId = requestId;
        this.authorizationId = authorizationId;
        this.orderId = orderId;
        this.operationType = operationType;
        this.amount = amount;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = createdAt;
    }

    public void requireSame(
        Long expectedAuthorizationId,
        Long expectedOrderId,
        OperationType expectedOperationType,
        BigDecimal expectedAmount
    ) {
        if (!authorizationId.equals(expectedAuthorizationId)
            || !Objects.equals(orderId, expectedOrderId)
            || operationType != expectedOperationType
            || amount.compareTo(expectedAmount) != 0) {
            throw new IllegalStateException(
                "Provider operation request ID conflict: " + requestId
            );
        }
    }

    public void recordTimeout(String timeoutReason) {
        attemptCount++;
        outcome = Outcome.TIMEOUT;
        reason = timeoutReason;
        providerReference = null;
        updatedAt = LocalDateTime.now();
    }

    public void markRetryExhausted(String timeoutReason) {
        if (attemptCount <= 0) {
            throw new IllegalStateException(
                "Cannot exhaust provider retry before an attempt has been recorded"
            );
        }
        outcome = Outcome.RETRY_EXHAUSTED;
        reason = timeoutReason;
        providerReference = null;
        updatedAt = LocalDateTime.now();
    }

    public void approve(String reference) {
        attemptCount++;
        outcome = Outcome.APPROVED;
        providerReference = reference;
        reason = null;
        updatedAt = LocalDateTime.now();
    }

    public void deny(String denialReason) {
        attemptCount++;
        outcome = Outcome.DENIED;
        providerReference = null;
        reason = denialReason;
        updatedAt = LocalDateTime.now();
    }

    public SettlementDecision decision() {
        if (outcome == Outcome.APPROVED) {
            return SettlementDecision.approved(providerReference);
        }
        if (outcome == Outcome.DENIED) {
            return SettlementDecision.denied(reason);
        }
        SettlementGatewayTimeoutException timeout = new SettlementGatewayTimeoutException(
            reason == null ? "Simulated settlement timeout" : reason
        );
        if (outcome == Outcome.RETRY_EXHAUSTED) {
            throw new SettlementRetryExhaustedException(
                operationType.name(),
                attemptCount,
                timeout
            );
        }
        throw timeout;
    }

    public Long getId() { return id; }
    public String getRequestId() { return requestId; }
    public Long getAuthorizationId() { return authorizationId; }
    public Long getOrderId() { return orderId; }
    public OperationType getOperationType() { return operationType; }
    public BigDecimal getAmount() { return amount; }
    public Outcome getOutcome() { return outcome; }
    public String getProviderReference() { return providerReference; }
    public String getReason() { return reason; }
    public int getAttemptCount() { return attemptCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
