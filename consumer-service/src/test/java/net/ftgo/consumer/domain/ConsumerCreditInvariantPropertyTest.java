package net.ftgo.consumer.domain;

import net.ftgo.common.Money;
import net.jqwik.api.*;
import net.jqwik.api.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;

/**
 * Property-based tests for Consumer Credit Invariant.
 * 
 * **Validates: Requirements 4.5**
 * 
 * Property 3: Consumer Credit Invariant
 * For any consumer account state, the available credit limit SHALL equal 
 * the total credit limit minus the sum of all reserved amounts.
 */
class ConsumerCreditInvariantPropertyTest {
    
    /**
     * Property 3: Consumer Credit Invariant
     * 
     * **Validates: Requirements 4.5**
     * 
     * Tests that availableCredit = creditLimit - sum(reservedAmounts) 
     * for all consumer states after any sequence of reserve/release operations.
     */
    @Property(tries = 100)
    void consumerCreditInvariantHoldsForAllStates(
            @ForAll("creditLimits") Money creditLimit,
            @ForAll("reserveOperations") List<ReserveOperation> operations) {
        
        // Create consumer with initial credit limit
        Consumer consumer = new Consumer("Test Consumer", "test@example.com", creditLimit);
        
        // Track total reserved amount
        Money totalReserved = Money.ZERO;
        
        // Apply all operations
        for (ReserveOperation operation : operations) {
            if (operation.isReserve()) {
                Money amount = operation.getAmount();
                // Only reserve if we have sufficient credit
                if (consumer.hasAvailableCredit(amount)) {
                    consumer.reserveCredit(amount);
                    totalReserved = totalReserved.add(amount);
                }
            } else {
                Money amount = operation.getAmount();
                // Release credit (can release up to total reserved)
                if (totalReserved.isGreaterThanOrEqual(amount)) {
                    consumer.releaseCredit(amount);
                    totalReserved = totalReserved.subtract(amount);
                }
            }
        }
        
        // Verify invariant: availableCredit = creditLimit - totalReserved
        Money expectedAvailableCredit = creditLimit.subtract(totalReserved);
        Money actualAvailableCredit = consumer.getAvailableCredit();
        
        // Assert the invariant holds
        if (!expectedAvailableCredit.equals(actualAvailableCredit)) {
            throw new AssertionError(
                String.format(
                    "Credit invariant violated! Expected available credit: %s, Actual: %s, " +
                    "Credit limit: %s, Total reserved: %s",
                    expectedAvailableCredit, actualAvailableCredit, creditLimit, totalReserved
                )
            );
        }
    }
    
    /**
     * Property 3: Consumer Credit Invariant (with credit limit updates)
     * 
     * **Validates: Requirements 4.5**
     * 
     * Tests that the invariant holds even when credit limits are updated.
     * Only tests scenarios where new credit limit >= current credit limit
     * (increasing credit limit scenarios).
     */
    @Property(tries = 100)
    void consumerCreditInvariantHoldsAfterCreditLimitIncrease(
            @ForAll("creditLimits") Money initialCreditLimit,
            @ForAll("reserveOperations") List<ReserveOperation> operations,
            @ForAll("creditLimitIncreases") Money creditLimitIncrease) {
        
        // Create consumer with initial credit limit
        Consumer consumer = new Consumer("Test Consumer", "test@example.com", initialCreditLimit);
        
        // Track total reserved amount
        Money totalReserved = Money.ZERO;
        
        // Apply reserve operations
        for (ReserveOperation operation : operations) {
            if (operation.isReserve()) {
                Money amount = operation.getAmount();
                if (consumer.hasAvailableCredit(amount)) {
                    consumer.reserveCredit(amount);
                    totalReserved = totalReserved.add(amount);
                }
            }
        }
        
        // Calculate new credit limit (always >= initial)
        Money newCreditLimit = initialCreditLimit.add(creditLimitIncrease);
        
        // Update credit limit
        consumer.updateCreditLimit(newCreditLimit);
        
        // Verify invariant: availableCredit = newCreditLimit - totalReserved
        Money expectedAvailableCredit = newCreditLimit.subtract(totalReserved);
        Money actualAvailableCredit = consumer.getAvailableCredit();
        
        // Assert the invariant holds
        if (!expectedAvailableCredit.equals(actualAvailableCredit)) {
            throw new AssertionError(
                String.format(
                    "Credit invariant violated after limit update! Expected available credit: %s, " +
                    "Actual: %s, New credit limit: %s, Total reserved: %s",
                    expectedAvailableCredit, actualAvailableCredit, newCreditLimit, totalReserved
                )
            );
        }
    }
    
    /**
     * Provides arbitrary credit limits (positive Money values).
     */
    @Provide
    Arbitrary<Money> creditLimits() {
        return Arbitraries.bigDecimals()
                .between(BigDecimal.valueOf(100), BigDecimal.valueOf(10000))
                .ofScale(2)
                .map(Money::new);
    }
    
    /**
     * Provides arbitrary credit limit increases (non-negative Money values).
     */
    @Provide
    Arbitrary<Money> creditLimitIncreases() {
        return Arbitraries.bigDecimals()
                .between(BigDecimal.ZERO, BigDecimal.valueOf(5000))
                .ofScale(2)
                .map(Money::new);
    }
    
    /**
     * Provides arbitrary sequences of reserve/release operations.
     */
    @Provide
    Arbitrary<List<ReserveOperation>> reserveOperations() {
        Arbitrary<ReserveOperation> reserveOp = Arbitraries.bigDecimals()
                .between(BigDecimal.valueOf(10), BigDecimal.valueOf(500))
                .ofScale(2)
                .map(amount -> new ReserveOperation(true, new Money(amount)));
        
        Arbitrary<ReserveOperation> releaseOp = Arbitraries.bigDecimals()
                .between(BigDecimal.valueOf(10), BigDecimal.valueOf(500))
                .ofScale(2)
                .map(amount -> new ReserveOperation(false, new Money(amount)));
        
        return Arbitraries.oneOf(reserveOp, releaseOp)
                .list()
                .ofMinSize(0)
                .ofMaxSize(20);
    }
    
    /**
     * Represents a reserve or release operation for property testing.
     */
    private static class ReserveOperation {
        private final boolean reserve;
        private final Money amount;
        
        public ReserveOperation(boolean reserve, Money amount) {
            this.reserve = reserve;
            this.amount = amount;
        }
        
        public boolean isReserve() {
            return reserve;
        }
        
        public Money getAmount() {
            return amount;
        }
        
        @Override
        public String toString() {
            return (reserve ? "Reserve " : "Release ") + amount;
        }
    }
}
