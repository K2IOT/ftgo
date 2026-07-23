package net.ftgo.accounting.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import net.ftgo.common.Money;

import java.time.LocalDateTime;

/**
 * Authorization entity representing a credit card authorization transaction.
 *
 * Each authorization is identified by a unique requestId (idempotency key) to ensure
 * duplicate authorization requests return the same result without creating new authorizations.
 */
@Entity
@Table(name = "authorizations")
public class Authorization {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Read-only view of the foreign key managed by Account.authorizations.
     * Account's unidirectional @OneToMany @JoinColumn is the single writer for
     * account_id; mapping this property as writable would duplicate the column.
     */
    @Column(name = "account_id", nullable = false, insertable = false, updatable = false)
    private Long accountId;

    @NotBlank(message = "Request ID is required")
    @Column(name = "request_id", nullable = false, unique = true)
    private String requestId;

    @NotNull(message = "Amount is required")
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "amount", column = @Column(name = "amount", nullable = false, precision = 10, scale = 2))
    })
    private Money amount;

    @NotNull(message = "Status is required")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private AuthorizationStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "reversed_at")
    private LocalDateTime reversedAt;

    protected Authorization() {
    }

    public Authorization(Long accountId, String requestId, Money amount, AuthorizationStatus status) {
        validateAccountId(accountId);
        validateRequestId(requestId);
        validateAmount(amount);
        validateStatus(status);

        this.accountId = accountId;
        this.requestId = requestId;
        this.amount = amount;
        this.status = status;
        this.createdAt = LocalDateTime.now();
    }

    public Authorization(String requestId, Money amount, AuthorizationStatus status) {
        validateRequestId(requestId);
        validateAmount(amount);
        validateStatus(status);

        this.requestId = requestId;
        this.amount = amount;
        this.status = status;
        this.createdAt = LocalDateTime.now();
    }

    private void validateAccountId(Long accountId) {
        if (accountId == null) {
            throw new IllegalArgumentException("Account ID cannot be null");
        }
    }

    private void validateRequestId(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("Request ID cannot be null or blank");
        }
    }

    private void validateAmount(Money amount) {
        if (amount == null) {
            throw new IllegalArgumentException("Amount cannot be null");
        }
        if (amount.getAmount().signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
    }

    private void validateStatus(AuthorizationStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("Status cannot be null");
        }
    }

    public void reverse() {
        if (status == AuthorizationStatus.REVERSED) {
            throw new IllegalStateException("Authorization is already reversed");
        }
        if (status == AuthorizationStatus.DENIED) {
            throw new IllegalStateException("Cannot reverse a denied authorization");
        }

        this.status = AuthorizationStatus.REVERSED;
        this.reversedAt = LocalDateTime.now();
    }

    public boolean isApproved() {
        return status == AuthorizationStatus.APPROVED;
    }

    public boolean isReversed() {
        return status == AuthorizationStatus.REVERSED;
    }

    public boolean isDenied() {
        return status == AuthorizationStatus.DENIED;
    }

    public Long getId() {
        return id;
    }

    public Long getAccountId() {
        return accountId;
    }

    public String getRequestId() {
        return requestId;
    }

    public Money getAmount() {
        return amount;
    }

    public AuthorizationStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getReversedAt() {
        return reversedAt;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
