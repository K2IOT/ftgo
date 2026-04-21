package net.ftgo.consumer.domain;

import net.ftgo.common.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Consumer aggregate.
 */
class ConsumerTest {
    
    @Test
    void testCreateConsumer() {
        Money creditLimit = new Money(new BigDecimal("1000.00"));
        Consumer consumer = new Consumer("John Doe", "john@example.com", creditLimit);
        
        assertEquals("John Doe", consumer.getName());
        assertEquals("john@example.com", consumer.getEmail());
        assertEquals(creditLimit, consumer.getCreditLimit());
        assertEquals(creditLimit, consumer.getAvailableCredit());
        assertNotNull(consumer.getCreatedAt());
        assertNotNull(consumer.getUpdatedAt());
    }
    
    @Test
    void testCreateConsumerWithNullCreditLimit() {
        assertThrows(IllegalArgumentException.class, () -> {
            new Consumer("John Doe", "john@example.com", null);
        });
    }
    
    @Test
    void testCreateConsumerWithZeroCreditLimit() {
        Money zeroCreditLimit = new Money(BigDecimal.ZERO);
        assertThrows(IllegalArgumentException.class, () -> {
            new Consumer("John Doe", "john@example.com", zeroCreditLimit);
        });
    }
    
    @Test
    void testCreateConsumerWithNegativeCreditLimit() {
        assertThrows(IllegalArgumentException.class, () -> {
            new Consumer("John Doe", "john@example.com", new Money(new BigDecimal("-100.00")));
        });
    }
    
    @Test
    void testHasAvailableCredit() {
        Money creditLimit = new Money(new BigDecimal("1000.00"));
        Consumer consumer = new Consumer("John Doe", "john@example.com", creditLimit);
        
        assertTrue(consumer.hasAvailableCredit(new Money(new BigDecimal("500.00"))));
        assertTrue(consumer.hasAvailableCredit(new Money(new BigDecimal("1000.00"))));
        assertFalse(consumer.hasAvailableCredit(new Money(new BigDecimal("1000.01"))));
    }
    
    @Test
    void testReserveCredit() {
        Money creditLimit = new Money(new BigDecimal("1000.00"));
        Consumer consumer = new Consumer("John Doe", "john@example.com", creditLimit);
        
        Money reserveAmount = new Money(new BigDecimal("300.00"));
        consumer.reserveCredit(reserveAmount);
        
        assertEquals(new Money(new BigDecimal("700.00")), consumer.getAvailableCredit());
    }
    
    @Test
    void testReserveCreditInsufficientFunds() {
        Money creditLimit = new Money(new BigDecimal("1000.00"));
        Consumer consumer = new Consumer("John Doe", "john@example.com", creditLimit);
        
        Money reserveAmount = new Money(new BigDecimal("1500.00"));
        assertThrows(IllegalArgumentException.class, () -> {
            consumer.reserveCredit(reserveAmount);
        });
    }
    
    @Test
    void testReleaseCredit() {
        Money creditLimit = new Money(new BigDecimal("1000.00"));
        Consumer consumer = new Consumer("John Doe", "john@example.com", creditLimit);
        
        // Reserve some credit
        consumer.reserveCredit(new Money(new BigDecimal("300.00")));
        assertEquals(new Money(new BigDecimal("700.00")), consumer.getAvailableCredit());
        
        // Release credit
        consumer.releaseCredit(new Money(new BigDecimal("300.00")));
        assertEquals(new Money(new BigDecimal("1000.00")), consumer.getAvailableCredit());
    }
    
    @Test
    void testReleaseCreditDoesNotExceedLimit() {
        Money creditLimit = new Money(new BigDecimal("1000.00"));
        Consumer consumer = new Consumer("John Doe", "john@example.com", creditLimit);
        
        // Try to release more credit than was reserved
        consumer.releaseCredit(new Money(new BigDecimal("500.00")));
        
        // Available credit should not exceed credit limit
        assertEquals(creditLimit, consumer.getAvailableCredit());
    }
    
    @Test
    void testUpdateCreditLimit() {
        Money initialCreditLimit = new Money(new BigDecimal("1000.00"));
        Consumer consumer = new Consumer("John Doe", "john@example.com", initialCreditLimit);
        
        // Reserve some credit
        consumer.reserveCredit(new Money(new BigDecimal("300.00")));
        assertEquals(new Money(new BigDecimal("700.00")), consumer.getAvailableCredit());
        
        // Increase credit limit
        Money newCreditLimit = new Money(new BigDecimal("1500.00"));
        consumer.updateCreditLimit(newCreditLimit);
        
        assertEquals(newCreditLimit, consumer.getCreditLimit());
        // Available credit should increase by the difference
        assertEquals(new Money(new BigDecimal("1200.00")), consumer.getAvailableCredit());
    }
    
    @Test
    void testUpdateCreditLimitWithInvalidValue() {
        Money creditLimit = new Money(new BigDecimal("1000.00"));
        Consumer consumer = new Consumer("John Doe", "john@example.com", creditLimit);
        
        assertThrows(IllegalArgumentException.class, () -> {
            consumer.updateCreditLimit(new Money(BigDecimal.ZERO));
        });
    }
    
    @Test
    void testUpdateProfile() {
        Money creditLimit = new Money(new BigDecimal("1000.00"));
        Consumer consumer = new Consumer("John Doe", "john@example.com", creditLimit);
        
        consumer.updateProfile("Jane Doe", "jane@example.com");
        
        assertEquals("Jane Doe", consumer.getName());
        assertEquals("jane@example.com", consumer.getEmail());
    }
    
    @Test
    void testCreditInvariant() {
        // Test that availableCredit = creditLimit - reservedAmounts
        Money creditLimit = new Money(new BigDecimal("1000.00"));
        Consumer consumer = new Consumer("John Doe", "john@example.com", creditLimit);
        
        // Reserve multiple amounts
        consumer.reserveCredit(new Money(new BigDecimal("200.00")));
        consumer.reserveCredit(new Money(new BigDecimal("150.00")));
        consumer.reserveCredit(new Money(new BigDecimal("100.00")));
        
        // Total reserved: 450.00
        // Available should be: 1000.00 - 450.00 = 550.00
        assertEquals(new Money(new BigDecimal("550.00")), consumer.getAvailableCredit());
        
        // Release some credit
        consumer.releaseCredit(new Money(new BigDecimal("150.00")));
        
        // Available should be: 550.00 + 150.00 = 700.00
        assertEquals(new Money(new BigDecimal("700.00")), consumer.getAvailableCredit());
    }
}
