package net.ftgo.consumer.domain;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import net.ftgo.common.Money;

import java.time.LocalDateTime;

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

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Consumer() {
    }

    public Consumer(String name, String email, Money creditLimit) {
        validateCreditLimit(creditLimit);
        this.name = name;
        this.email = email;
        this.creditLimit = creditLimit;
        this.availableCredit = creditLimit;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    private void validateCreditLimit(Money value) {
        if (value == null || value.isZero()) {
            throw new IllegalArgumentException("Credit limit must be positive");
        }
    }

    public boolean hasAvailableCredit(Money amount) {
        return amount != null && availableCredit.isGreaterThanOrEqual(amount);
    }

    public void reserveCredit(Money amount) {
        if (amount == null || amount.isZero()) {
            throw new IllegalArgumentException("Reservation amount must be positive");
        }
        if (!hasAvailableCredit(amount)) {
            throw new IllegalArgumentException(
                "Insufficient credit. Available: " + availableCredit + ", Required: " + amount);
        }
        availableCredit = availableCredit.subtract(amount);
        updatedAt = LocalDateTime.now();
    }

    public void releaseCredit(Money amount) {
        if (amount == null || amount.isZero()) {
            throw new IllegalArgumentException("Release amount must be positive");
        }
        availableCredit = availableCredit.add(amount);
        if (availableCredit.isGreaterThan(creditLimit)) {
            availableCredit = creditLimit;
        }
        updatedAt = LocalDateTime.now();
    }

    public void updateCreditLimit(Money newCreditLimit) {
        validateCreditLimit(newCreditLimit);
        if (newCreditLimit.isGreaterThan(creditLimit)) {
            availableCredit = availableCredit.add(newCreditLimit.subtract(creditLimit));
        } else if (creditLimit.isGreaterThan(newCreditLimit)) {
            Money decrease = creditLimit.subtract(newCreditLimit);
            if (availableCredit.isLessThan(decrease)) {
                throw new CreditLimitBelowReservedAmountException();
            }
            availableCredit = availableCredit.subtract(decrease);
        }
        creditLimit = newCreditLimit;
        updatedAt = LocalDateTime.now();
    }

    public void updateProfile(String name, String email) {
        if (name != null && !name.isBlank()) this.name = name;
        if (email != null && !email.isBlank()) this.email = email;
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public Money getCreditLimit() { return creditLimit; }
    public Money getAvailableCredit() { return availableCredit; }
    public Long getVersion() { return version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    @PrePersist
    protected void onCreate() {
        if (version == null) version = 0L;
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
