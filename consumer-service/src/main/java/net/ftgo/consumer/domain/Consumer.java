package net.ftgo.consumer.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import net.ftgo.common.Money;

import java.time.LocalDateTime;

/**
 * Consumer aggregate representing a customer account with credit limit management.
 * 
 * Invariant: availableCredit = creditLimit - reservedAmounts
 */
@Entity
@Table(name = "consumers")
public class Consumer {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @NotBlank(message = "Name is required")
    @Column(nullable = false)
    private String name;
    
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    @Column(nullable = false, unique = true)
    private String email;
    
    @NotNull(message = "Credit limit is required")
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "amount", column = @Column(name = "credit_limit", nullable = false, precision = 10, scale = 2))
    })
    private Money creditLimit;
    
    @NotNull
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "amount", column = @Column(name = "available_credit", nullable = false, precision = 10, scale = 2))
    })
    private Money availableCredit;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    /**
     * Default constructor for JPA.
     */
    protected Consumer() {
    }
    
    /**
     * Creates a new Consumer with the specified details.
     * 
     * @param name the consumer's name
     * @param email the consumer's email address
     * @param creditLimit the consumer's credit limit (must be positive)
     * @throws IllegalArgumentException if creditLimit is not positive
     */
    public Consumer(String name, String email, Money creditLimit) {
        validateCreditLimit(creditLimit);
        
        this.name = name;
        this.email = email;
        this.creditLimit = creditLimit;
        this.availableCredit = creditLimit; // Initially, all credit is available
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
    
    /**
     * Validates that the credit limit is a positive decimal value.
     * 
     * @param creditLimit the credit limit to validate
     * @throws IllegalArgumentException if creditLimit is null or not positive
     */
    private void validateCreditLimit(Money creditLimit) {
        if (creditLimit == null) {
            throw new IllegalArgumentException("Credit limit cannot be null");
        }
        if (creditLimit.getAmount().signum() <= 0) {
            throw new IllegalArgumentException("Credit limit must be a positive decimal value");
        }
    }
    
    /**
     * Verifies if the consumer has sufficient available credit for the specified amount.
     * 
     * @param orderTotal the amount to verify
     * @return true if available credit is sufficient, false otherwise
     */
    public boolean hasAvailableCredit(Money orderTotal) {
        return availableCredit.isGreaterThanOrEqual(orderTotal);
    }
    
    /**
     * Reserves credit for an order.
     * 
     * @param amount the amount to reserve
     * @throws IllegalArgumentException if insufficient credit is available
     */
    public void reserveCredit(Money amount) {
        if (!hasAvailableCredit(amount)) {
            throw new IllegalArgumentException(
                String.format("Insufficient credit. Available: %s, Required: %s", 
                    availableCredit, amount)
            );
        }
        this.availableCredit = availableCredit.subtract(amount);
        this.updatedAt = LocalDateTime.now();
    }
    
    /**
     * Releases previously reserved credit.
     * 
     * @param amount the amount to release
     */
    public void releaseCredit(Money amount) {
        this.availableCredit = availableCredit.add(amount);
        // Ensure available credit doesn't exceed total credit limit
        if (availableCredit.isGreaterThan(creditLimit)) {
            this.availableCredit = creditLimit;
        }
        this.updatedAt = LocalDateTime.now();
    }
    
    /**
     * Updates the consumer's credit limit.
     * 
     * @param newCreditLimit the new credit limit (must be positive)
     * @throws IllegalArgumentException if newCreditLimit is not positive
     */
    public void updateCreditLimit(Money newCreditLimit) {
        validateCreditLimit(newCreditLimit);
        
        Money difference = newCreditLimit.subtract(this.creditLimit);
        this.creditLimit = newCreditLimit;
        this.availableCredit = availableCredit.add(difference);
        this.updatedAt = LocalDateTime.now();
    }
    
    /**
     * Updates the consumer's profile information.
     * 
     * @param name the new name
     * @param email the new email
     */
    public void updateProfile(String name, String email) {
        if (name != null && !name.isBlank()) {
            this.name = name;
        }
        if (email != null && !email.isBlank()) {
            this.email = email;
        }
        this.updatedAt = LocalDateTime.now();
    }
    
    // Getters
    
    public Long getId() {
        return id;
    }
    
    public String getName() {
        return name;
    }
    
    public String getEmail() {
        return email;
    }
    
    public Money getCreditLimit() {
        return creditLimit;
    }
    
    public Money getAvailableCredit() {
        return availableCredit;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
