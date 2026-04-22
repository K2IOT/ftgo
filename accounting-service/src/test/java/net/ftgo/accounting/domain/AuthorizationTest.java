package net.ftgo.accounting.domain;

import net.ftgo.common.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Authorization entity.
 */
class AuthorizationTest {
    
    @Test
    void testCreateAuthorization() {
        Long accountId = 1L;
        String requestId = "req-001";
        Money amount = new Money(new BigDecimal("100.00"));
        AuthorizationStatus status = AuthorizationStatus.APPROVED;
        
        Authorization auth = new Authorization(accountId, requestId, amount, status);
        
        assertEquals(accountId, auth.getAccountId());
        assertEquals(requestId, auth.getRequestId());
        assertEquals(amount, auth.getAmount());
        assertEquals(status, auth.getStatus());
        assertNotNull(auth.getCreatedAt());
        assertNull(auth.getReversedAt());
    }
    
    @Test
    void testCreateAuthorizationWithNullAccountId() {
        assertThrows(IllegalArgumentException.class, () -> {
            new Authorization(null, "req-001", 
                new Money(new BigDecimal("100.00")), AuthorizationStatus.APPROVED);
        });
    }
    
    @Test
    void testCreateAuthorizationWithNullRequestId() {
        assertThrows(IllegalArgumentException.class, () -> {
            new Authorization(1L, null, 
                new Money(new BigDecimal("100.00")), AuthorizationStatus.APPROVED);
        });
    }
    
    @Test
    void testCreateAuthorizationWithBlankRequestId() {
        assertThrows(IllegalArgumentException.class, () -> {
            new Authorization(1L, "", 
                new Money(new BigDecimal("100.00")), AuthorizationStatus.APPROVED);
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            new Authorization(1L, "   ", 
                new Money(new BigDecimal("100.00")), AuthorizationStatus.APPROVED);
        });
    }
    
    @Test
    void testCreateAuthorizationWithNullAmount() {
        assertThrows(IllegalArgumentException.class, () -> {
            new Authorization(1L, "req-001", null, AuthorizationStatus.APPROVED);
        });
    }
    
    @Test
    void testCreateAuthorizationWithZeroAmount() {
        assertThrows(IllegalArgumentException.class, () -> {
            new Authorization(1L, "req-001", 
                new Money(BigDecimal.ZERO), AuthorizationStatus.APPROVED);
        });
    }
    
    @Test
    void testCreateAuthorizationWithNegativeAmount() {
        assertThrows(IllegalArgumentException.class, () -> {
            new Authorization(1L, "req-001", 
                new Money(new BigDecimal("-100.00")), AuthorizationStatus.APPROVED);
        });
    }
    
    @Test
    void testCreateAuthorizationWithNullStatus() {
        assertThrows(IllegalArgumentException.class, () -> {
            new Authorization(1L, "req-001", 
                new Money(new BigDecimal("100.00")), null);
        });
    }
    
    @Test
    void testReverseAuthorization() {
        Authorization auth = new Authorization(1L, "req-001", 
            new Money(new BigDecimal("100.00")), AuthorizationStatus.APPROVED);
        
        assertFalse(auth.isReversed());
        assertNull(auth.getReversedAt());
        
        auth.reverse();
        
        assertTrue(auth.isReversed());
        assertEquals(AuthorizationStatus.REVERSED, auth.getStatus());
        assertNotNull(auth.getReversedAt());
    }
    
    @Test
    void testReverseAlreadyReversedAuthorization() {
        Authorization auth = new Authorization(1L, "req-001", 
            new Money(new BigDecimal("100.00")), AuthorizationStatus.APPROVED);
        
        auth.reverse();
        
        assertThrows(IllegalStateException.class, () -> {
            auth.reverse();
        });
    }
    
    @Test
    void testReverseDeniedAuthorization() {
        Authorization auth = new Authorization(1L, "req-001", 
            new Money(new BigDecimal("100.00")), AuthorizationStatus.DENIED);
        
        assertThrows(IllegalStateException.class, () -> {
            auth.reverse();
        });
    }
    
    @Test
    void testIsApproved() {
        Authorization approved = new Authorization(1L, "req-001", 
            new Money(new BigDecimal("100.00")), AuthorizationStatus.APPROVED);
        assertTrue(approved.isApproved());
        
        Authorization denied = new Authorization(1L, "req-002", 
            new Money(new BigDecimal("100.00")), AuthorizationStatus.DENIED);
        assertFalse(denied.isApproved());
        
        Authorization reversed = new Authorization(1L, "req-003", 
            new Money(new BigDecimal("100.00")), AuthorizationStatus.REVERSED);
        assertFalse(reversed.isApproved());
    }
    
    @Test
    void testIsReversed() {
        Authorization auth = new Authorization(1L, "req-001", 
            new Money(new BigDecimal("100.00")), AuthorizationStatus.APPROVED);
        assertFalse(auth.isReversed());
        
        auth.reverse();
        assertTrue(auth.isReversed());
    }
    
    @Test
    void testIsDenied() {
        Authorization denied = new Authorization(1L, "req-001", 
            new Money(new BigDecimal("100.00")), AuthorizationStatus.DENIED);
        assertTrue(denied.isDenied());
        
        Authorization approved = new Authorization(1L, "req-002", 
            new Money(new BigDecimal("100.00")), AuthorizationStatus.APPROVED);
        assertFalse(approved.isDenied());
    }
    
    @Test
    void testAuthorizationStatusTransitions() {
        // Test valid transition: APPROVED -> REVERSED
        Authorization auth = new Authorization(1L, "req-001", 
            new Money(new BigDecimal("100.00")), AuthorizationStatus.APPROVED);
        
        assertEquals(AuthorizationStatus.APPROVED, auth.getStatus());
        auth.reverse();
        assertEquals(AuthorizationStatus.REVERSED, auth.getStatus());
    }
}
