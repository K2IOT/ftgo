package net.ftgo.accounting.domain;

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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import net.ftgo.common.Money;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "authorizations")
public class Authorization {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false, insertable = false, updatable = false)
    private Long accountId;

    @Column(name = "order_id")
    private Long orderId;

    @NotBlank
    @Column(name = "request_id", nullable = false, unique = true)
    private String requestId;

    @NotNull
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "amount", column = @Column(name = "amount", nullable = false, precision = 10, scale = 2))
    })
    private Money amount;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private AuthorizationStatus status;

    @Column(name = "capture_request_id", unique = true)
    private String captureRequestId;

    @Column(name = "void_request_id", unique = true)
    private String voidRequestId;

    @Column(name = "refund_request_id", unique = true)
    private String refundRequestId;

    @Column(name = "void_reason")
    private String voidReason;

    @Column(name = "refund_reason")
    private String refundReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "captured_at")
    private LocalDateTime capturedAt;

    @Column(name = "voided_at")
    private LocalDateTime voidedAt;

    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

    @Column(name = "reversed_at")
    private LocalDateTime reversedAt;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    protected Authorization() {
    }

    public Authorization(Long accountId, String requestId, Money amount, AuthorizationStatus status) {
        validateAccountId(accountId);
        initialize(null, requestId, amount, status);
        this.accountId = accountId;
    }

    public Authorization(String requestId, Money amount, AuthorizationStatus status) {
        initialize(null, requestId, amount, status);
    }

    public Authorization(Long orderId, String requestId, Money amount) {
        initialize(orderId, requestId, amount, AuthorizationStatus.AUTHORIZED);
    }

    public Authorization(Long accountId, Long orderId, String requestId,
                         Money amount, AuthorizationStatus status) {
        validateAccountId(accountId);
        initialize(orderId, requestId, amount, status);
        this.accountId = accountId;
    }

    private void initialize(Long orderId, String requestId, Money amount, AuthorizationStatus status) {
        validateRequestId(requestId);
        validateAmount(amount);
        if (status == null) throw new IllegalArgumentException("Status cannot be null");
        this.orderId = orderId;
        this.requestId = requestId;
        this.amount = amount;
        this.status = status;
        this.createdAt = LocalDateTime.now();
    }

    private void validateAccountId(Long value) {
        if (value == null) throw new IllegalArgumentException("Account ID cannot be null");
    }

    private void validateRequestId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Request ID cannot be null or blank");
        }
    }

    private void validateAmount(Money value) {
        if (value == null || value.isZero()) {
            throw new IllegalArgumentException("Amount must be positive");
        }
    }

    public boolean capture(String operationRequestId) {
        validateRequestId(operationRequestId);
        if (status == AuthorizationStatus.CAPTURED) {
            if (Objects.equals(captureRequestId, operationRequestId)) return false;
            throw new IllegalStateException("Authorization is already captured with another request");
        }
        if (status != AuthorizationStatus.AUTHORIZED && status != AuthorizationStatus.APPROVED) {
            throw new IllegalStateException("Cannot capture authorization in state " + status);
        }
        status = AuthorizationStatus.CAPTURED;
        captureRequestId = operationRequestId;
        capturedAt = LocalDateTime.now();
        return true;
    }

    public boolean voidAuthorization(String reason, String operationRequestId) {
        validateRequestId(operationRequestId);
        if (status == AuthorizationStatus.VOIDED) {
            if (Objects.equals(voidRequestId, operationRequestId)) return false;
            throw new IllegalStateException("Authorization is already voided with another request");
        }
        if (status != AuthorizationStatus.AUTHORIZED && status != AuthorizationStatus.APPROVED) {
            throw new IllegalStateException("Cannot void authorization in state " + status);
        }
        status = AuthorizationStatus.VOIDED;
        voidReason = reason;
        voidRequestId = operationRequestId;
        voidedAt = LocalDateTime.now();
        reversedAt = voidedAt;
        return true;
    }

    public boolean refund(Money refundAmount, String reason, String operationRequestId) {
        validateRequestId(operationRequestId);
        if (status == AuthorizationStatus.REFUNDED) {
            if (Objects.equals(refundRequestId, operationRequestId)) return false;
            throw new IllegalStateException("Payment is already refunded with another request");
        }
        if (status != AuthorizationStatus.CAPTURED) {
            throw new IllegalStateException("Cannot refund authorization in state " + status);
        }
        if (!amount.equals(refundAmount)) {
            throw new IllegalArgumentException("Refund must equal the full authorization amount");
        }
        status = AuthorizationStatus.REFUNDED;
        refundReason = reason;
        refundRequestId = operationRequestId;
        refundedAt = LocalDateTime.now();
        return true;
    }

    /** Legacy adapter used by the pre-Phase-02 Cancel/Revise sagas. */
    public void reverse() {
        if (status == AuthorizationStatus.REVERSED || status == AuthorizationStatus.VOIDED) {
            throw new IllegalStateException("Authorization is already reversed");
        }
        if (status == AuthorizationStatus.DENIED) {
            throw new IllegalStateException("Cannot reverse a denied authorization");
        }
        if (status == AuthorizationStatus.CAPTURED || status == AuthorizationStatus.REFUNDED) {
            throw new IllegalStateException("Captured payment requires refund");
        }
        status = status == AuthorizationStatus.APPROVED
            ? AuthorizationStatus.REVERSED
            : AuthorizationStatus.VOIDED;
        reversedAt = LocalDateTime.now();
        if (status == AuthorizationStatus.VOIDED) voidedAt = reversedAt;
    }

    public boolean matches(Long expectedOrderId, Money expectedAmount) {
        return Objects.equals(orderId, expectedOrderId) && amount.equals(expectedAmount);
    }

    public boolean isApproved() {
        return status == AuthorizationStatus.APPROVED || status == AuthorizationStatus.AUTHORIZED;
    }

    public boolean isReversed() {
        return status == AuthorizationStatus.REVERSED || status == AuthorizationStatus.VOIDED;
    }

    public boolean isDenied() { return status == AuthorizationStatus.DENIED; }

    public Long getId() { return id; }
    public Long getAccountId() { return accountId; }
    public Long getOrderId() { return orderId; }
    public String getRequestId() { return requestId; }
    public Money getAmount() { return amount; }
    public AuthorizationStatus getStatus() { return status; }
    public String getCaptureRequestId() { return captureRequestId; }
    public String getVoidRequestId() { return voidRequestId; }
    public String getRefundRequestId() { return refundRequestId; }
    public String getVoidReason() { return voidReason; }
    public String getRefundReason() { return refundReason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getCapturedAt() { return capturedAt; }
    public LocalDateTime getVoidedAt() { return voidedAt; }
    public LocalDateTime getRefundedAt() { return refundedAt; }
    public LocalDateTime getReversedAt() { return reversedAt; }
    public Long getVersion() { return version; }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (version == null) version = 0L;
    }
}
