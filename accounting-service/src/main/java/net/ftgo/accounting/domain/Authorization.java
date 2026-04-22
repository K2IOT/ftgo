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
    
    @NotNull(message = "Account ID is required")
    @Column(name = "account_id", nullable = false)
    private Long accountId;
    
    @NotBlank(message = "Request ID is required")
    @Column(name = "request_id", nullable = false, unique = true)
    private String requestId; // Idempotency key
    
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
    
    /**
     * Default constructor for JPA.
     */
    protected Authorization() {
    }
    
    /**
     * Creates a new Authorization with the specified details.
     * 
     * @param accountId the account ID this authorization belongs to
     * @param requestId the unique request ID (idempotency key)
     * @param amount the authorization amount
     * @param status the authorization status
     * @throws IllegalArgumentException if any required field is null or invalid
     */
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
    
    /**
     * Creates a new Authorization without accountId (will be set by JPA).
     * Used when creating authorizations within an Account aggregate.
     * 
     * @param requestId the unique request ID (idempotency key)
     * @param amount the authorization amount
     * @param status the authorization status
     * @throws IllegalArgumentException if any required field is null or invalid
     */
    public Authorization(String requestId, Money amount, AuthorizationStatus status) {
        validateRequestId(requestId);
        validateAmount(amount);
        validateStatus(status);
        
        this.requestId = requestId;
        this.amount = amount;
        this.status = status;
        this.createdAt = LocalDateTime.now();
    }
    
    /**
     * Validates that the account ID is not null.
     * 
     * @param accountId the account ID to validate
     * @throws IllegalArgumentException if accountId is null
     */
    private void validateAccountId(Long accountId) {
        if (accountId == null) {
            throw new IllegalArgumentException("Account ID cannot be null");
        }
    }
    
    /**
     * Validates that the request ID is not null or blank.
     * 
     * @param requestId the request ID to validate
     * @throws IllegalArgumentException if requestId is null or blank
     */
    private void validateRequestId(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("Request ID cannot be null or blank");
        }
    }
    
    /**
     * Validates that the amount is not null and is positive.
     * 
     * @param amount the amount to validate
     * @throws IllegalArgumentException if amount is null or not positive
     */
    private void validateAmount(Money amount) {
        if (amount == null) {
            throw new IllegalArgumentException("Amount cannot be null");
        }
        if (amount.getAmount().signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
    }
    
    /**
     * Validates that the status is not null.
     * 
     * @param status the status to validate
     * @throws IllegalArgumentException if status is null
     */
    private void validateStatus(AuthorizationStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("Status cannot be null");
        }
    }
    
    /**
     * Reverses this authorization.
     * 
     * @throws IllegalStateException if the authorization is already reversed or denied
     */
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
    
    /**
     * Checks if this authorization is approved.
     * 
     * @return true if status is APPROVED, false otherwise
     */
    public boolean isApproved() {
        return status == AuthorizationStatus.APPROVED;
    }
    
    /**
     * Checks if this authorization is reversed.
     * 
     * @return true if status is REVERSED, false otherwise
     */
    public boolean isReversed() {
        return status == AuthorizationStatus.REVERSED;
    }
    
    /**
     * Checks if this authorization is denied.
     * 
     * @return true if status is DENIED, false otherwise
     */
    public boolean isDenied() {
        return status == AuthorizationStatus.DENIED;
    }
    
    // Getters
    
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
