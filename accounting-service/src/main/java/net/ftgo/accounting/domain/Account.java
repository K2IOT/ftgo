package net.ftgo.accounting.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import net.ftgo.common.Money;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Account aggregate representing a consumer's payment account.
 * 
 * Manages credit card authorizations with idempotent processing using requestId as the idempotency key.
 * Duplicate authorization requests with the same requestId return the cached result without creating
 * a new authorization.
 */
@Entity
@Table(name = "accounts")
public class Account {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @NotNull(message = "Consumer ID is required")
    @Column(name = "consumer_id", nullable = false, unique = true)
    private Long consumerId;
    
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private List<Authorization> authorizations = new ArrayList<>();
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    /**
     * Default constructor for JPA.
     */
    protected Account() {
    }
    
    /**
     * Creates a new Account for the specified consumer.
     * 
     * @param consumerId the consumer ID this account belongs to
     * @throws IllegalArgumentException if consumerId is null
     */
    public Account(Long consumerId) {
        validateConsumerId(consumerId);
        
        this.consumerId = consumerId;
        this.createdAt = LocalDateTime.now();
    }
    
    /**
     * Validates that the consumer ID is not null.
     * 
     * @param consumerId the consumer ID to validate
     * @throws IllegalArgumentException if consumerId is null
     */
    private void validateConsumerId(Long consumerId) {
        if (consumerId == null) {
            throw new IllegalArgumentException("Consumer ID cannot be null");
        }
    }
    
    /**
     * Authorizes a credit card transaction with idempotent processing.
     * 
     * If an authorization with the same requestId already exists, returns the cached result
     * without creating a new authorization (idempotency guarantee).
     * 
     * @param requestId the unique request ID (idempotency key)
     * @param amount the amount to authorize
     * @return the authorization (existing or newly created)
     * @throws IllegalArgumentException if requestId or amount is invalid
     */
    public Authorization authorize(String requestId, Money amount) {
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("Request ID cannot be null or blank");
        }
        if (amount == null) {
            throw new IllegalArgumentException("Amount cannot be null");
        }
        
        // Idempotency check: return existing authorization if requestId already exists
        Authorization existing = findAuthorizationByRequestId(requestId);
        if (existing != null) {
            return existing; // Return cached result
        }
        
        // Create new authorization
        // In a real system, this would call a payment gateway API
        // For this implementation, we'll approve all authorizations
        // Note: accountId will be set by JPA when the account is persisted
        Authorization authorization = new Authorization(
            requestId,
            amount,
            AuthorizationStatus.APPROVED
        );
        
        authorizations.add(authorization);
        return authorization;
    }
    
    /**
     * Reverses an existing authorization.
     * 
     * @param authorizationId the ID of the authorization to reverse
     * @throws IllegalArgumentException if authorization is not found
     * @throws IllegalStateException if authorization cannot be reversed
     */
    public void reverseAuthorization(Long authorizationId) {
        Authorization authorization = findAuthorizationById(authorizationId);
        if (authorization == null) {
            throw new IllegalArgumentException(
                String.format("Authorization with ID %d not found", authorizationId)
            );
        }
        
        authorization.reverse();
    }
    
    /**
     * Reverses an authorization by request ID.
     * 
     * @param requestId the request ID of the authorization to reverse
     * @throws IllegalArgumentException if authorization is not found
     * @throws IllegalStateException if authorization cannot be reversed
     */
    public void reverseAuthorizationByRequestId(String requestId) {
        Authorization authorization = findAuthorizationByRequestId(requestId);
        if (authorization == null) {
            throw new IllegalArgumentException(
                String.format("Authorization with request ID %s not found", requestId)
            );
        }
        
        authorization.reverse();
    }
    
    /**
     * Revises an existing authorization to a new amount.
     * 
     * This creates a new authorization with the new amount and reverses the old one.
     * In a real system, this would call the payment gateway to adjust the authorization.
     * 
     * @param authorizationId the ID of the authorization to revise
     * @param newAmount the new authorization amount
     * @param newRequestId the request ID for the new authorization (for idempotency)
     * @return the new authorization
     * @throws IllegalArgumentException if authorization is not found or amounts are invalid
     * @throws IllegalStateException if authorization cannot be revised
     */
    public Authorization reviseAuthorization(Long authorizationId, Money newAmount, String newRequestId) {
        if (newRequestId == null || newRequestId.isBlank()) {
            throw new IllegalArgumentException("New request ID cannot be null or blank");
        }
        if (newAmount == null) {
            throw new IllegalArgumentException("New amount cannot be null");
        }
        if (newAmount.getAmount().signum() <= 0) {
            throw new IllegalArgumentException("New amount must be positive");
        }
        
        // Check idempotency before inspecting the original authorization state. A retry of a
        // successful revision sees the original authorization as REVERSED, but must still return
        // the authorization created by the first request.
        Authorization existing = findAuthorizationByRequestId(newRequestId);
        if (existing != null) {
            return existing;
        }

        // Find the existing authorization only for a genuinely new revision request.
        Authorization existingAuth = findAuthorizationById(authorizationId);
        if (existingAuth == null) {
            throw new IllegalArgumentException(
                String.format("Authorization with ID %d not found", authorizationId)
            );
        }
        
        // Check if authorization can be revised
        if (existingAuth.isReversed()) {
            throw new IllegalStateException("Cannot revise a reversed authorization");
        }
        if (existingAuth.isDenied()) {
            throw new IllegalStateException("Cannot revise a denied authorization");
        }
        
        // Reverse the old authorization
        existingAuth.reverse();
        
        // Create new authorization with the new amount
        // Note: accountId will be set by JPA when the account is persisted
        Authorization newAuth = new Authorization(
            newRequestId,
            newAmount,
            AuthorizationStatus.APPROVED
        );
        
        authorizations.add(newAuth);
        return newAuth;
    }
    
    /**
     * Finds an authorization by its ID.
     * 
     * @param authorizationId the authorization ID to search for
     * @return the authorization if found, null otherwise
     */
    private Authorization findAuthorizationById(Long authorizationId) {
        return authorizations.stream()
            .filter(auth -> auth.getId() != null && auth.getId().equals(authorizationId))
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Finds an authorization by its request ID (idempotency key).
     * 
     * @param requestId the request ID to search for
     * @return the authorization if found, null otherwise
     */
    private Authorization findAuthorizationByRequestId(String requestId) {
        return authorizations.stream()
            .filter(auth -> auth.getRequestId().equals(requestId))
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Gets all authorizations for this account.
     * 
     * @return an unmodifiable list of authorizations
     */
    public List<Authorization> getAuthorizations() {
        return List.copyOf(authorizations);
    }
    
    // Getters
    
    public Long getId() {
        return id;
    }
    
    public Long getConsumerId() {
        return consumerId;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
