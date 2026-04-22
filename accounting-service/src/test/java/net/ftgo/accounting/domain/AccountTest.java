package net.ftgo.accounting.domain;

import net.ftgo.common.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Account aggregate.
 * 
 * Tests authorization idempotency, reversal, and revision functionality.
 */
class AccountTest {
    
    @Test
    void testCreateAccount() {
        Long consumerId = 123L;
        Account account = new Account(consumerId);
        
        assertEquals(consumerId, account.getConsumerId());
        assertNotNull(account.getCreatedAt());
        assertTrue(account.getAuthorizations().isEmpty());
    }
    
    @Test
    void testCreateAccountWithNullConsumerId() {
        assertThrows(IllegalArgumentException.class, () -> {
            new Account(null);
        });
    }
    
    // ========== Authorization Idempotency Tests ==========
    
    @Test
    void testAuthorizeCard() {
        Account account = new Account(123L);
        Money amount = new Money(new BigDecimal("100.00"));
        String requestId = "req-001";
        
        Authorization auth = account.authorize(requestId, amount);
        
        assertNotNull(auth);
        assertEquals(requestId, auth.getRequestId());
        assertEquals(amount, auth.getAmount());
        assertEquals(AuthorizationStatus.APPROVED, auth.getStatus());
        assertNotNull(auth.getCreatedAt());
        assertNull(auth.getReversedAt());
        assertEquals(1, account.getAuthorizations().size());
    }
    
    @Test
    void testAuthorizationIdempotency_SameRequestIdReturnsSameResult() {
        // This is the key idempotency test: same requestId should return cached result
        Account account = new Account(123L);
        Money amount = new Money(new BigDecimal("100.00"));
        String requestId = "req-idempotent-001";
        
        // First authorization
        Authorization auth1 = account.authorize(requestId, amount);
        assertNotNull(auth1);
        assertEquals(1, account.getAuthorizations().size());
        
        // Second authorization with same requestId (should return cached result)
        Authorization auth2 = account.authorize(requestId, amount);
        assertNotNull(auth2);
        
        // Should return the same authorization object
        assertSame(auth1, auth2, "Same requestId should return the same authorization");
        
        // Should not create a new authorization
        assertEquals(1, account.getAuthorizations().size(), 
            "Duplicate requestId should not create new authorization");
    }
    
    @Test
    void testAuthorizationIdempotency_DifferentRequestIdsCreateNewAuthorizations() {
        Account account = new Account(123L);
        Money amount = new Money(new BigDecimal("100.00"));
        
        // First authorization
        Authorization auth1 = account.authorize("req-001", amount);
        assertEquals(1, account.getAuthorizations().size());
        
        // Second authorization with different requestId
        Authorization auth2 = account.authorize("req-002", amount);
        assertEquals(2, account.getAuthorizations().size());
        
        // Should be different authorizations
        assertNotSame(auth1, auth2);
        assertNotEquals(auth1.getRequestId(), auth2.getRequestId());
    }
    
    @Test
    void testAuthorizationIdempotency_MultipleCallsWithSameRequestId() {
        Account account = new Account(123L);
        Money amount = new Money(new BigDecimal("250.00"));
        String requestId = "req-multi-001";
        
        // Call authorize multiple times with same requestId
        Authorization auth1 = account.authorize(requestId, amount);
        Authorization auth2 = account.authorize(requestId, amount);
        Authorization auth3 = account.authorize(requestId, amount);
        
        // All should return the same authorization
        assertSame(auth1, auth2);
        assertSame(auth2, auth3);
        
        // Only one authorization should exist
        assertEquals(1, account.getAuthorizations().size());
    }
    
    @Test
    void testAuthorizeWithNullRequestId() {
        Account account = new Account(123L);
        Money amount = new Money(new BigDecimal("100.00"));
        
        assertThrows(IllegalArgumentException.class, () -> {
            account.authorize(null, amount);
        });
    }
    
    @Test
    void testAuthorizeWithBlankRequestId() {
        Account account = new Account(123L);
        Money amount = new Money(new BigDecimal("100.00"));
        
        assertThrows(IllegalArgumentException.class, () -> {
            account.authorize("", amount);
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            account.authorize("   ", amount);
        });
    }
    
    @Test
    void testAuthorizeWithNullAmount() {
        Account account = new Account(123L);
        
        assertThrows(IllegalArgumentException.class, () -> {
            account.authorize("req-001", null);
        });
    }
    
    // ========== Authorization Reversal Tests ==========
    
    @Test
    void testReverseAuthorization() {
        Account account = new Account(123L);
        Money amount = new Money(new BigDecimal("100.00"));
        
        // Create authorization
        Authorization auth = account.authorize("req-001", amount);
        Long authId = auth.getId();
        
        // Manually set ID for testing (normally set by JPA)
        setAuthorizationId(auth, 1L);
        
        // Reverse authorization
        account.reverseAuthorization(1L);
        
        // Verify authorization is reversed
        assertTrue(auth.isReversed());
        assertEquals(AuthorizationStatus.REVERSED, auth.getStatus());
        assertNotNull(auth.getReversedAt());
    }
    
    @Test
    void testReverseAuthorizationByRequestId() {
        Account account = new Account(123L);
        Money amount = new Money(new BigDecimal("150.00"));
        String requestId = "req-reverse-001";
        
        // Create authorization
        Authorization auth = account.authorize(requestId, amount);
        
        // Reverse by request ID
        account.reverseAuthorizationByRequestId(requestId);
        
        // Verify authorization is reversed
        assertTrue(auth.isReversed());
        assertEquals(AuthorizationStatus.REVERSED, auth.getStatus());
        assertNotNull(auth.getReversedAt());
    }
    
    @Test
    void testReverseAuthorizationNotFound() {
        Account account = new Account(123L);
        
        assertThrows(IllegalArgumentException.class, () -> {
            account.reverseAuthorization(999L);
        });
    }
    
    @Test
    void testReverseAuthorizationByRequestIdNotFound() {
        Account account = new Account(123L);
        
        assertThrows(IllegalArgumentException.class, () -> {
            account.reverseAuthorizationByRequestId("non-existent-request-id");
        });
    }
    
    @Test
    void testReverseAlreadyReversedAuthorization() {
        Account account = new Account(123L);
        Money amount = new Money(new BigDecimal("100.00"));
        String requestId = "req-double-reverse";
        
        // Create and reverse authorization
        Authorization auth = account.authorize(requestId, amount);
        setAuthorizationId(auth, 1L);
        account.reverseAuthorization(1L);
        
        // Try to reverse again
        assertThrows(IllegalStateException.class, () -> {
            account.reverseAuthorization(1L);
        });
    }
    
    // ========== Authorization Revision Tests ==========
    
    @Test
    void testReviseAuthorization() {
        Account account = new Account(123L);
        Money originalAmount = new Money(new BigDecimal("100.00"));
        Money newAmount = new Money(new BigDecimal("150.00"));
        String originalRequestId = "req-original";
        String newRequestId = "req-revised";
        
        // Create original authorization
        Authorization originalAuth = account.authorize(originalRequestId, originalAmount);
        setAuthorizationId(originalAuth, 1L);
        
        // Revise authorization
        Authorization newAuth = account.reviseAuthorization(1L, newAmount, newRequestId);
        
        // Verify original authorization is reversed
        assertTrue(originalAuth.isReversed());
        assertEquals(AuthorizationStatus.REVERSED, originalAuth.getStatus());
        
        // Verify new authorization is created
        assertNotNull(newAuth);
        assertEquals(newRequestId, newAuth.getRequestId());
        assertEquals(newAmount, newAuth.getAmount());
        assertEquals(AuthorizationStatus.APPROVED, newAuth.getStatus());
        assertFalse(newAuth.isReversed());
        
        // Verify account has both authorizations
        assertEquals(2, account.getAuthorizations().size());
    }
    
    @Test
    void testReviseAuthorizationIdempotency() {
        Account account = new Account(123L);
        Money originalAmount = new Money(new BigDecimal("100.00"));
        Money newAmount = new Money(new BigDecimal("200.00"));
        String originalRequestId = "req-original-idempotent";
        String newRequestId = "req-revised-idempotent";
        
        // Create original authorization
        Authorization originalAuth = account.authorize(originalRequestId, originalAmount);
        setAuthorizationId(originalAuth, 1L);
        
        // First revision
        Authorization newAuth1 = account.reviseAuthorization(1L, newAmount, newRequestId);
        assertNotNull(newAuth1);
        setAuthorizationId(newAuth1, 2L);
        
        // Second revision attempt with same newRequestId (idempotency check)
        // This should detect that newRequestId already exists and return the cached result
        // We need to try revising the NEW authorization (not the old one which is already reversed)
        Authorization newAuth2 = account.reviseAuthorization(2L, newAmount, newRequestId);
        
        // Should return the same authorization (idempotency)
        assertSame(newAuth1, newAuth2, "Same newRequestId should return cached result");
        
        // Should only have 2 authorizations (original + one new)
        assertEquals(2, account.getAuthorizations().size());
    }
    
    @Test
    void testReviseAuthorizationNotFound() {
        Account account = new Account(123L);
        Money newAmount = new Money(new BigDecimal("150.00"));
        
        assertThrows(IllegalArgumentException.class, () -> {
            account.reviseAuthorization(999L, newAmount, "req-new");
        });
    }
    
    @Test
    void testReviseReversedAuthorization() {
        Account account = new Account(123L);
        Money amount = new Money(new BigDecimal("100.00"));
        Money newAmount = new Money(new BigDecimal("150.00"));
        String requestId = "req-reversed";
        
        // Create and reverse authorization
        Authorization auth = account.authorize(requestId, amount);
        setAuthorizationId(auth, 1L);
        account.reverseAuthorization(1L);
        
        // Try to revise reversed authorization
        assertThrows(IllegalStateException.class, () -> {
            account.reviseAuthorization(1L, newAmount, "req-new");
        });
    }
    
    @Test
    void testReviseAuthorizationWithNullNewRequestId() {
        Account account = new Account(123L);
        Money amount = new Money(new BigDecimal("100.00"));
        Money newAmount = new Money(new BigDecimal("150.00"));
        
        Authorization auth = account.authorize("req-001", amount);
        setAuthorizationId(auth, 1L);
        
        assertThrows(IllegalArgumentException.class, () -> {
            account.reviseAuthorization(1L, newAmount, null);
        });
    }
    
    @Test
    void testReviseAuthorizationWithBlankNewRequestId() {
        Account account = new Account(123L);
        Money amount = new Money(new BigDecimal("100.00"));
        Money newAmount = new Money(new BigDecimal("150.00"));
        
        Authorization auth = account.authorize("req-001", amount);
        setAuthorizationId(auth, 1L);
        
        assertThrows(IllegalArgumentException.class, () -> {
            account.reviseAuthorization(1L, newAmount, "");
        });
    }
    
    @Test
    void testReviseAuthorizationWithNullNewAmount() {
        Account account = new Account(123L);
        Money amount = new Money(new BigDecimal("100.00"));
        
        Authorization auth = account.authorize("req-001", amount);
        setAuthorizationId(auth, 1L);
        
        assertThrows(IllegalArgumentException.class, () -> {
            account.reviseAuthorization(1L, null, "req-new");
        });
    }
    
    @Test
    void testReviseAuthorizationWithZeroAmount() {
        Account account = new Account(123L);
        Money amount = new Money(new BigDecimal("100.00"));
        Money zeroAmount = new Money(BigDecimal.ZERO);
        
        Authorization auth = account.authorize("req-001", amount);
        setAuthorizationId(auth, 1L);
        
        assertThrows(IllegalArgumentException.class, () -> {
            account.reviseAuthorization(1L, zeroAmount, "req-new");
        });
    }
    
    @Test
    void testReviseAuthorizationWithNegativeAmount() {
        Account account = new Account(123L);
        Money amount = new Money(new BigDecimal("100.00"));
        
        Authorization auth = account.authorize("req-001", amount);
        setAuthorizationId(auth, 1L);
        
        // Negative amount should be rejected when creating Money object
        assertThrows(IllegalArgumentException.class, () -> {
            Money negativeAmount = new Money(new BigDecimal("-50.00"));
            account.reviseAuthorization(1L, negativeAmount, "req-new");
        });
    }
    
    // ========== Complex Scenarios ==========
    
    @Test
    void testMultipleAuthorizationsAndReversals() {
        Account account = new Account(123L);
        
        // Create multiple authorizations
        Authorization auth1 = account.authorize("req-001", new Money(new BigDecimal("100.00")));
        Authorization auth2 = account.authorize("req-002", new Money(new BigDecimal("200.00")));
        Authorization auth3 = account.authorize("req-003", new Money(new BigDecimal("150.00")));
        
        setAuthorizationId(auth1, 1L);
        setAuthorizationId(auth2, 2L);
        setAuthorizationId(auth3, 3L);
        
        assertEquals(3, account.getAuthorizations().size());
        
        // Reverse one authorization
        account.reverseAuthorization(2L);
        
        // Verify states
        assertFalse(auth1.isReversed());
        assertTrue(auth2.isReversed());
        assertFalse(auth3.isReversed());
        
        // All authorizations still in list
        assertEquals(3, account.getAuthorizations().size());
    }
    
    @Test
    void testAuthorizationRevisionChain() {
        Account account = new Account(123L);
        
        // Original authorization
        Authorization auth1 = account.authorize("req-v1", new Money(new BigDecimal("100.00")));
        setAuthorizationId(auth1, 1L);
        
        // First revision
        Authorization auth2 = account.reviseAuthorization(1L, 
            new Money(new BigDecimal("150.00")), "req-v2");
        setAuthorizationId(auth2, 2L);
        
        // Second revision
        Authorization auth3 = account.reviseAuthorization(2L, 
            new Money(new BigDecimal("200.00")), "req-v3");
        
        // Verify chain
        assertTrue(auth1.isReversed(), "Original should be reversed");
        assertTrue(auth2.isReversed(), "First revision should be reversed");
        assertFalse(auth3.isReversed(), "Latest revision should be active");
        
        assertEquals(new Money(new BigDecimal("200.00")), auth3.getAmount());
        assertEquals(3, account.getAuthorizations().size());
    }
    
    @Test
    void testGetAuthorizations() {
        Account account = new Account(123L);
        
        account.authorize("req-001", new Money(new BigDecimal("100.00")));
        account.authorize("req-002", new Money(new BigDecimal("200.00")));
        
        var authorizations = account.getAuthorizations();
        
        assertEquals(2, authorizations.size());
        
        // Verify returned list is unmodifiable
        assertThrows(UnsupportedOperationException.class, () -> {
            authorizations.add(new Authorization(1L, "req-003", 
                new Money(new BigDecimal("50.00")), AuthorizationStatus.APPROVED));
        });
    }
    
    // ========== Helper Methods ==========
    
    /**
     * Helper method to set authorization ID using reflection (simulates JPA behavior).
     */
    private void setAuthorizationId(Authorization auth, Long id) {
        try {
            var idField = Authorization.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(auth, id);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set authorization ID", e);
        }
    }
}
