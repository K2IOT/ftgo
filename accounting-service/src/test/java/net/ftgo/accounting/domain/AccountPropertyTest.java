package net.ftgo.accounting.domain;

import net.ftgo.common.Money;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.NotBlank;
import net.jqwik.api.constraints.Positive;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for Account aggregate using jqwik.
 * 
 * These tests validate universal properties that should hold for all valid inputs:
 * - Property 4: Authorization Idempotency (Requirements 7.7)
 * - Property 8: Saga Compensation Correctness (Requirements 2.8, 7.8, 12.8)
 */
class AccountPropertyTest {
    
    // ========== Property 4: Authorization Idempotency ==========
    
    /**
     * Property 4: Authorization Idempotency
     * 
     * Validates: Requirements 7.7
     * 
     * For any authorization request with a given requestId, processing the request
     * multiple times SHALL produce the same outcome (return the same authorization
     * without creating duplicates).
     * 
     * This property ensures that the system can safely handle duplicate requests
     * (e.g., due to network retries, client retries, or message redelivery) without
     * creating multiple authorizations for the same logical request.
     */
    @Property(tries = 100)
    @Label("Property 4: Authorization Idempotency - Same requestId produces same outcome")
    void authorizationIdempotency_sameRequestIdProducesSameOutcome(
        @ForAll @Positive long consumerId,
        @ForAll @NotBlank String requestId,
        @ForAll @Positive BigDecimal amount,
        @ForAll @IntRange(min = 2, max = 10) int numberOfCalls
    ) {
        // Given: An account
        Account account = new Account(consumerId);
        Money money = new Money(amount);
        
        // When: We call authorize multiple times with the same requestId
        List<Authorization> results = new ArrayList<>();
        for (int i = 0; i < numberOfCalls; i++) {
            Authorization auth = account.authorize(requestId, money);
            results.add(auth);
        }
        
        // Then: All calls should return the same authorization object
        Authorization firstAuth = results.get(0);
        for (int i = 1; i < results.size(); i++) {
            assertSame(firstAuth, results.get(i),
                String.format("Call %d should return the same authorization as call 1", i + 1));
        }
        
        // And: Only one authorization should be created
        assertEquals(1, account.getAuthorizations().size(),
            "Only one authorization should exist despite multiple calls with same requestId");
        
        // And: The authorization should have the correct properties
        assertEquals(requestId, firstAuth.getRequestId());
        assertEquals(money, firstAuth.getAmount());
        assertEquals(AuthorizationStatus.APPROVED, firstAuth.getStatus());
    }
    
    /**
     * Property 4 (variant): Authorization Idempotency with Different Amounts
     * 
     * Even if the amount differs in subsequent calls with the same requestId,
     * the original authorization should be returned (idempotency key takes precedence).
     */
    @Property(tries = 100)
    @Label("Property 4: Authorization Idempotency - Same requestId returns original even with different amount")
    void authorizationIdempotency_sameRequestIdReturnsSameAuthorizationEvenWithDifferentAmount(
        @ForAll @Positive long consumerId,
        @ForAll @NotBlank String requestId,
        @ForAll @Positive BigDecimal originalAmount,
        @ForAll @Positive BigDecimal differentAmount
    ) {
        Assume.that(!originalAmount.equals(differentAmount));
        
        // Given: An account with an existing authorization
        Account account = new Account(consumerId);
        Money originalMoney = new Money(originalAmount);
        Authorization originalAuth = account.authorize(requestId, originalMoney);
        
        // When: We call authorize again with the same requestId but different amount
        Money differentMoney = new Money(differentAmount);
        Authorization secondAuth = account.authorize(requestId, differentMoney);
        
        // Then: Should return the original authorization (idempotency)
        assertSame(originalAuth, secondAuth,
            "Same requestId should return original authorization regardless of amount");
        
        // And: The amount should be the original amount (not the new one)
        assertEquals(originalMoney, secondAuth.getAmount(),
            "Returned authorization should have original amount");
        
        // And: Only one authorization should exist
        assertEquals(1, account.getAuthorizations().size());
    }
    
    /**
     * Property 4 (variant): Different Request IDs Create Different Authorizations
     * 
     * Validates that different requestIds create separate authorizations,
     * ensuring the idempotency mechanism doesn't incorrectly merge distinct requests.
     */
    @Property(tries = 100)
    @Label("Property 4: Different requestIds create different authorizations")
    void authorizationIdempotency_differentRequestIdsCreateDifferentAuthorizations(
        @ForAll @Positive long consumerId,
        @ForAll @NotBlank String requestId1,
        @ForAll @NotBlank String requestId2,
        @ForAll @Positive BigDecimal amount
    ) {
        Assume.that(!requestId1.equals(requestId2));
        
        // Given: An account
        Account account = new Account(consumerId);
        Money money = new Money(amount);
        
        // When: We authorize with two different requestIds
        Authorization auth1 = account.authorize(requestId1, money);
        Authorization auth2 = account.authorize(requestId2, money);
        
        // Then: Two different authorizations should be created
        assertNotSame(auth1, auth2,
            "Different requestIds should create different authorizations");
        
        // And: Both should be in the account
        assertEquals(2, account.getAuthorizations().size());
        
        // And: Each should have its own requestId
        assertEquals(requestId1, auth1.getRequestId());
        assertEquals(requestId2, auth2.getRequestId());
    }
    
    // ========== Property 8: Saga Compensation Correctness ==========
    
    /**
     * Property 8: Saga Compensation Correctness
     * 
     * Validates: Requirements 2.8, 7.8, 12.8
     * 
     * For any authorization, reversing it then re-authorizing with a new requestId
     * SHALL be equivalent to never having reversed it (compensation correctness).
     * 
     * This property ensures that saga compensation logic correctly restores the system
     * to a consistent state. In the context of the CancelOrderSaga, if the saga fails
     * after reversing an authorization, the compensation should be able to re-authorize
     * and restore the original state.
     * 
     * Mathematical property: reverse(authorize(x)) then authorize(x') ≡ authorize(x')
     */
    @Property(tries = 100)
    @Label("Property 8: Saga Compensation Correctness - Reverse then re-authorize restores state")
    void sagaCompensationCorrectness_reverseThenReauthorizeRestoresState(
        @ForAll @Positive long consumerId,
        @ForAll @NotBlank String originalRequestId,
        @ForAll @NotBlank String newRequestId,
        @ForAll @Positive BigDecimal amount
    ) {
        Assume.that(!originalRequestId.equals(newRequestId));
        
        // Given: An account with an authorization
        Account account = new Account(consumerId);
        Money money = new Money(amount);
        Authorization originalAuth = account.authorize(originalRequestId, money);
        setAuthorizationId(originalAuth, 1L);
        
        // When: We reverse the authorization (compensation step)
        account.reverseAuthorization(1L);
        
        // And: Re-authorize with a new requestId (forward step after compensation)
        Authorization newAuth = account.authorize(newRequestId, money);
        
        // Then: The new authorization should be approved and active
        assertEquals(AuthorizationStatus.APPROVED, newAuth.getStatus(),
            "Re-authorization should be approved");
        assertFalse(newAuth.isReversed(),
            "Re-authorization should not be reversed");
        
        // And: The new authorization should have the same amount
        assertEquals(money, newAuth.getAmount(),
            "Re-authorization should have the same amount");
        
        // And: The original authorization should remain reversed
        assertTrue(originalAuth.isReversed(),
            "Original authorization should remain reversed");
        
        // And: Both authorizations should exist in the account
        assertEquals(2, account.getAuthorizations().size(),
            "Both original and new authorizations should exist");
        
        // And: The account should have exactly one active (non-reversed) authorization
        long activeCount = account.getAuthorizations().stream()
            .filter(auth -> !auth.isReversed())
            .count();
        assertEquals(1, activeCount,
            "Account should have exactly one active authorization after compensation");
    }
    
    /**
     * Property 8 (variant): Saga Compensation with Revision
     * 
     * Tests that revising an authorization (which internally reverses the old one
     * and creates a new one) produces a consistent state equivalent to having
     * only the new authorization.
     */
    @Property(tries = 100)
    @Label("Property 8: Saga Compensation Correctness - Revision produces consistent state")
    void sagaCompensationCorrectness_revisionProducesConsistentState(
        @ForAll @Positive long consumerId,
        @ForAll @NotBlank String originalRequestId,
        @ForAll @NotBlank String revisedRequestId,
        @ForAll @Positive BigDecimal originalAmount,
        @ForAll @Positive BigDecimal revisedAmount
    ) {
        Assume.that(!originalRequestId.equals(revisedRequestId));
        
        // Given: An account with an authorization
        Account account = new Account(consumerId);
        Money originalMoney = new Money(originalAmount);
        Authorization originalAuth = account.authorize(originalRequestId, originalMoney);
        setAuthorizationId(originalAuth, 1L);
        
        // When: We revise the authorization (ReviseOrderSaga)
        Money revisedMoney = new Money(revisedAmount);
        Authorization revisedAuth = account.reviseAuthorization(1L, revisedMoney, revisedRequestId);
        
        // Then: The revised authorization should be approved and active
        assertEquals(AuthorizationStatus.APPROVED, revisedAuth.getStatus(),
            "Revised authorization should be approved");
        assertFalse(revisedAuth.isReversed(),
            "Revised authorization should not be reversed");
        assertEquals(revisedMoney, revisedAuth.getAmount(),
            "Revised authorization should have the new amount");
        
        // And: The original authorization should be reversed
        assertTrue(originalAuth.isReversed(),
            "Original authorization should be reversed after revision");
        
        // And: The account should have exactly one active authorization
        long activeCount = account.getAuthorizations().stream()
            .filter(auth -> !auth.isReversed())
            .count();
        assertEquals(1, activeCount,
            "Account should have exactly one active authorization after revision");
        
        // And: The active authorization should be the revised one
        Authorization activeAuth = account.getAuthorizations().stream()
            .filter(auth -> !auth.isReversed())
            .findFirst()
            .orElseThrow();
        assertSame(revisedAuth, activeAuth,
            "The active authorization should be the revised one");
    }
    
    /**
     * Property 8 (variant): Multiple Compensation Cycles
     * 
     * Tests that multiple cycles of authorization and reversal maintain consistency.
     * This simulates scenarios where sagas fail and retry multiple times.
     */
    @Property(tries = 100)
    @Label("Property 8: Saga Compensation Correctness - Multiple compensation cycles maintain consistency")
    void sagaCompensationCorrectness_multipleCompensationCyclesMaintainConsistency(
        @ForAll @Positive long consumerId,
        @ForAll @Positive BigDecimal amount,
        @ForAll @IntRange(min = 2, max = 5) int numberOfCycles
    ) {
        // Given: An account
        Account account = new Account(consumerId);
        Money money = new Money(amount);
        
        // When: We perform multiple authorization-reversal cycles
        List<Authorization> authorizations = new ArrayList<>();
        for (int i = 0; i < numberOfCycles; i++) {
            String requestId = "req-cycle-" + i;
            Authorization auth = account.authorize(requestId, money);
            setAuthorizationId(auth, (long) (i + 1));
            authorizations.add(auth);
            
            // Reverse the authorization (compensation)
            account.reverseAuthorization((long) (i + 1));
        }
        
        // And: Create one final active authorization
        String finalRequestId = "req-final";
        Authorization finalAuth = account.authorize(finalRequestId, money);
        
        // Then: All previous authorizations should be reversed
        for (int i = 0; i < numberOfCycles; i++) {
            assertTrue(authorizations.get(i).isReversed(),
                String.format("Authorization %d should be reversed", i));
        }
        
        // And: The final authorization should be active
        assertFalse(finalAuth.isReversed(),
            "Final authorization should be active");
        assertEquals(AuthorizationStatus.APPROVED, finalAuth.getStatus(),
            "Final authorization should be approved");
        
        // And: The account should have exactly one active authorization
        long activeCount = account.getAuthorizations().stream()
            .filter(auth -> !auth.isReversed())
            .count();
        assertEquals(1, activeCount,
            "Account should have exactly one active authorization");
        
        // And: Total authorizations should equal cycles + 1
        assertEquals(numberOfCycles + 1, account.getAuthorizations().size(),
            "Total authorizations should equal number of cycles plus final authorization");
    }
    
    /**
     * Property 8 (variant): Compensation Idempotency
     * 
     * Tests that attempting to reverse an already-reversed authorization
     * maintains idempotency (throws exception but doesn't corrupt state).
     */
    @Property(tries = 100)
    @Label("Property 8: Saga Compensation Correctness - Compensation is idempotent")
    void sagaCompensationCorrectness_compensationIsIdempotent(
        @ForAll @Positive long consumerId,
        @ForAll @NotBlank String requestId,
        @ForAll @Positive BigDecimal amount
    ) {
        // Given: An account with a reversed authorization
        Account account = new Account(consumerId);
        Money money = new Money(amount);
        Authorization auth = account.authorize(requestId, money);
        setAuthorizationId(auth, 1L);
        account.reverseAuthorization(1L);
        
        // When: We attempt to reverse it again
        // Then: Should throw exception (idempotent behavior - prevents double reversal)
        assertThrows(IllegalStateException.class, () -> {
            account.reverseAuthorization(1L);
        });
        
        // And: The authorization should still be reversed (state unchanged)
        assertTrue(auth.isReversed(),
            "Authorization should remain reversed");
        assertEquals(AuthorizationStatus.REVERSED, auth.getStatus(),
            "Authorization status should remain REVERSED");
        
        // And: Account state should be consistent
        assertEquals(1, account.getAuthorizations().size(),
            "Account should still have one authorization");
    }
    
    // ========== Arbitraries (Data Generators) ==========
    
    /**
     * Provides arbitrary positive BigDecimal values for amounts.
     * Generates amounts between 0.01 and 10000.00 with 2 decimal places.
     */
    @Provide
    Arbitrary<BigDecimal> positiveAmounts() {
        return Arbitraries.bigDecimals()
            .between(new BigDecimal("0.01"), new BigDecimal("10000.00"))
            .ofScale(2);
    }
    
    /**
     * Provides arbitrary positive long values for consumer IDs.
     */
    @Provide
    Arbitrary<Long> positiveConsumerIds() {
        return Arbitraries.longs().between(1L, Long.MAX_VALUE);
    }
    
    /**
     * Provides arbitrary non-blank request IDs.
     */
    @Provide
    Arbitrary<String> requestIds() {
        return Arbitraries.strings()
            .alpha()
            .numeric()
            .withChars('-', '_')
            .ofMinLength(5)
            .ofMaxLength(50);
    }
    
    // ========== Helper Methods ==========
    
    /**
     * Helper method to set authorization ID using reflection (simulates JPA behavior).
     * In production, JPA would set this automatically when persisting.
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
