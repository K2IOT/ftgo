package net.ftgo.accounting.domain;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import net.ftgo.common.Money;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "payment_captures")
public class PaymentCapture {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "authorization_id", nullable = false)
    private Authorization authorization;

    @Column(name = "request_id", nullable = false, unique = true, length = 255)
    private String requestId;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "amount", column = @Column(name = "amount", nullable = false, precision = 10, scale = 2))
    })
    private Money amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FinancialOperationStatus status;

    @Column(name = "provider_capture_id", unique = true, length = 255)
    private String providerCaptureId;

    @Column(name = "failure_code", length = 100)
    private String failureCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    protected PaymentCapture() {
    }

    PaymentCapture(Authorization authorization, String requestId, Money amount) {
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        requireText(requestId, "requestId");
        requirePositive(amount);
        this.requestId = requestId;
        this.amount = amount;
        this.status = FinancialOperationStatus.PENDING;
        this.createdAt = LocalDateTime.now();
    }

    public boolean complete(String providerCaptureId) {
        requireText(providerCaptureId, "providerCaptureId");
        if (status == FinancialOperationStatus.SUCCEEDED) {
            if (Objects.equals(this.providerCaptureId, providerCaptureId)) return false;
            throw new IllegalStateException("Capture already completed with another provider reference");
        }
        if (status == FinancialOperationStatus.FAILED) {
            throw new IllegalStateException("Failed capture cannot transition to succeeded");
        }
        status = FinancialOperationStatus.SUCCEEDED;
        this.providerCaptureId = providerCaptureId;
        completedAt = LocalDateTime.now();
        return true;
    }

    public boolean fail(String code) {
        requireText(code, "failureCode");
        if (status == FinancialOperationStatus.FAILED) {
            if (Objects.equals(failureCode, code)) return false;
            throw new IllegalStateException("Capture already failed with another code");
        }
        if (status == FinancialOperationStatus.SUCCEEDED) {
            throw new IllegalStateException("Successful capture cannot regress to failed");
        }
        status = FinancialOperationStatus.FAILED;
        failureCode = code;
        completedAt = LocalDateTime.now();
        return true;
    }

    boolean matches(Money expectedAmount) {
        return amount.equals(expectedAmount);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be null or blank");
        }
    }

    private static void requirePositive(Money value) {
        if (value == null || value.isZero()) {
            throw new IllegalArgumentException("Capture amount must be positive");
        }
    }

    @PrePersist
    protected void onCreate() {
        if (status == null) status = FinancialOperationStatus.PENDING;
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (version == null) version = 0L;
    }

    public Long getId() { return id; }
    public Authorization getAuthorization() { return authorization; }
    public String getRequestId() { return requestId; }
    public Money getAmount() { return amount; }
    public FinancialOperationStatus getStatus() { return status; }
    public String getProviderCaptureId() { return providerCaptureId; }
    public String getFailureCode() { return failureCode; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public Long getVersion() { return version; }
}
