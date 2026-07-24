package net.ftgo.accounting.domain;

import net.ftgo.common.Money;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Assume;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Property-based tests for authorization idempotency and saga compensation.
 * All request IDs come from the domain-valid requestIds provider instead of
 * jqwik's generic NotBlank constraint, which can emit control-only strings
 * that String.trim() treats as blank.
 */
class AccountPropertyTest {

    @Property(tries = 100)
    @Label("Property 4: Authorization Idempotency - Same requestId produces same outcome")
    void sameRequestIdProducesSameOutcome(
        @ForAll("consumerIds") long consumerId,
        @ForAll("requestIds") String requestId,
        @ForAll("amounts") BigDecimal amount,
        @ForAll @IntRange(min = 2, max = 10) int numberOfCalls
    ) {
        Account account = new Account(consumerId);
        Money money = new Money(amount);
        List<Authorization> results = new ArrayList<>();

        for (int i = 0; i < numberOfCalls; i++) {
            results.add(account.authorize(requestId, money));
        }

        Authorization first = results.getFirst();
        results.forEach(result -> assertSame(first, result));
        assertEquals(1, account.getAuthorizations().size());
        assertEquals(requestId, first.getRequestId());
        assertEquals(money, first.getAmount());
        assertEquals(AuthorizationStatus.APPROVED, first.getStatus());
    }

    @Property(tries = 100)
    @Label("Property 4: Authorization Idempotency - Same requestId returns original even with different amount")
    void sameRequestIdReturnsOriginalForDifferentAmount(
        @ForAll("consumerIds") long consumerId,
        @ForAll("requestIds") String requestId,
        @ForAll("amounts") BigDecimal originalAmount,
        @ForAll("amounts") BigDecimal differentAmount
    ) {
        Assume.that(!originalAmount.equals(differentAmount));
        Account account = new Account(consumerId);
        Authorization original = account.authorize(requestId, new Money(originalAmount));
        Authorization duplicate = account.authorize(requestId, new Money(differentAmount));

        assertSame(original, duplicate);
        assertEquals(new Money(originalAmount), duplicate.getAmount());
        assertEquals(1, account.getAuthorizations().size());
    }

    @Property(tries = 100)
    @Label("Property 4: Different requestIds create different authorizations")
    void differentRequestIdsCreateDifferentAuthorizations(
        @ForAll("consumerIds") long consumerId,
        @ForAll("requestIds") String requestId1,
        @ForAll("requestIds") String requestId2,
        @ForAll("amounts") BigDecimal amount
    ) {
        Assume.that(!requestId1.equals(requestId2));
        Account account = new Account(consumerId);
        Money money = new Money(amount);

        Authorization first = account.authorize(requestId1, money);
        Authorization second = account.authorize(requestId2, money);

        assertNotSame(first, second);
        assertEquals(2, account.getAuthorizations().size());
        assertEquals(requestId1, first.getRequestId());
        assertEquals(requestId2, second.getRequestId());
    }

    @Property(tries = 100)
    @Label("Property 8: Saga Compensation Correctness - Reverse then re-authorize restores state")
    void reverseThenReauthorizeRestoresState(
        @ForAll("consumerIds") long consumerId,
        @ForAll("requestIds") String originalRequestId,
        @ForAll("requestIds") String newRequestId,
        @ForAll("amounts") BigDecimal amount
    ) {
        Assume.that(!originalRequestId.equals(newRequestId));
        Account account = new Account(consumerId);
        Money money = new Money(amount);
        Authorization original = account.authorize(originalRequestId, money);
        setAuthorizationId(original, 1L);

        account.reverseAuthorization(1L);
        Authorization replacement = account.authorize(newRequestId, money);

        assertTrue(original.isReversed());
        assertFalse(replacement.isReversed());
        assertEquals(AuthorizationStatus.APPROVED, replacement.getStatus());
        assertEquals(money, replacement.getAmount());
        assertEquals(2, account.getAuthorizations().size());
        assertEquals(1, activeAuthorizationCount(account));
    }

    @Property(tries = 100)
    @Label("Property 8: Saga Compensation Correctness - Revision produces consistent state")
    void revisionProducesConsistentState(
        @ForAll("consumerIds") long consumerId,
        @ForAll("requestIds") String originalRequestId,
        @ForAll("requestIds") String revisedRequestId,
        @ForAll("amounts") BigDecimal originalAmount,
        @ForAll("amounts") BigDecimal revisedAmount
    ) {
        Assume.that(!originalRequestId.equals(revisedRequestId));
        Account account = new Account(consumerId);
        Authorization original = account.authorize(originalRequestId, new Money(originalAmount));
        setAuthorizationId(original, 1L);

        Authorization revised = account.reviseAuthorization(
            1L, new Money(revisedAmount), revisedRequestId);

        assertTrue(original.isReversed());
        assertFalse(revised.isReversed());
        assertEquals(AuthorizationStatus.APPROVED, revised.getStatus());
        assertEquals(new Money(revisedAmount), revised.getAmount());
        assertEquals(1, activeAuthorizationCount(account));
        assertSame(revised, account.getAuthorizations().stream()
            .filter(authorization -> !authorization.isReversed())
            .findFirst()
            .orElseThrow());
    }

    @Property(tries = 100)
    @Label("Property 8: Saga Compensation Correctness - Multiple compensation cycles maintain consistency")
    void multipleCompensationCyclesMaintainConsistency(
        @ForAll("consumerIds") long consumerId,
        @ForAll("amounts") BigDecimal amount,
        @ForAll @IntRange(min = 2, max = 5) int numberOfCycles
    ) {
        Account account = new Account(consumerId);
        Money money = new Money(amount);
        List<Authorization> reversed = new ArrayList<>();

        for (int i = 0; i < numberOfCycles; i++) {
            Authorization authorization = account.authorize("req-cycle-" + i, money);
            setAuthorizationId(authorization, (long) i + 1);
            reversed.add(authorization);
            account.reverseAuthorization((long) i + 1);
        }
        Authorization active = account.authorize("req-final", money);

        reversed.forEach(authorization -> assertTrue(authorization.isReversed()));
        assertFalse(active.isReversed());
        assertEquals(AuthorizationStatus.APPROVED, active.getStatus());
        assertEquals(1, activeAuthorizationCount(account));
        assertEquals(numberOfCycles + 1, account.getAuthorizations().size());
    }

    @Property(tries = 100)
    @Label("Property 8: Saga Compensation Correctness - Compensation is idempotent")
    void compensationIsIdempotent(
        @ForAll("consumerIds") long consumerId,
        @ForAll("requestIds") String requestId,
        @ForAll("amounts") BigDecimal amount
    ) {
        Account account = new Account(consumerId);
        Authorization authorization = account.authorize(requestId, new Money(amount));
        setAuthorizationId(authorization, 1L);
        account.reverseAuthorization(1L);

        assertThrows(IllegalStateException.class, () -> account.reverseAuthorization(1L));
        assertTrue(authorization.isReversed());
        assertEquals(AuthorizationStatus.REVERSED, authorization.getStatus());
        assertEquals(1, account.getAuthorizations().size());
    }

    @Provide
    Arbitrary<BigDecimal> amounts() {
        return Arbitraries.bigDecimals()
            .between(new BigDecimal("0.01"), new BigDecimal("10000.00"))
            .ofScale(2);
    }

    @Provide
    Arbitrary<Long> consumerIds() {
        return Arbitraries.longs().between(1L, Long.MAX_VALUE);
    }

    @Provide
    Arbitrary<String> requestIds() {
        return Arbitraries.strings()
            .alpha()
            .numeric()
            .withChars('-', '_')
            .ofMinLength(5)
            .ofMaxLength(50);
    }

    private long activeAuthorizationCount(Account account) {
        return account.getAuthorizations().stream()
            .filter(authorization -> !authorization.isReversed())
            .count();
    }

    private void setAuthorizationId(Authorization authorization, Long id) {
        try {
            var idField = Authorization.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(authorization, id);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to set authorization ID", e);
        }
    }
}
