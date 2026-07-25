package net.ftgo.accounting.domain;

import net.ftgo.common.Money;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinancialLifecycleTest {

    @Test
    void duplicateCaptureRequestReturnsOriginalOperation() {
        Authorization authorization = authorized("100.00");

        PaymentCapture first = authorization.requestCapture("capture-order-101");
        PaymentCapture duplicate = authorization.requestCapture("capture-order-101");

        assertSame(first, duplicate);
        assertEquals(1, authorization.getPaymentCaptures().size());
        assertEquals(FinancialOperationStatus.PENDING, first.getStatus());
    }

    @Test
    void successfulCaptureIsMonotonicAndPreventsSecondCapture() {
        Authorization authorization = authorized("100.00");
        PaymentCapture capture = authorization.requestCapture("capture-order-101");

        assertTrue(authorization.completeCapture("capture-order-101", "pc_101"));
        assertFalse(authorization.completeCapture("capture-order-101", "pc_101"));
        assertEquals(AuthorizationStatus.CAPTURED, authorization.getStatus());
        assertEquals(FinancialOperationStatus.SUCCEEDED, capture.getStatus());
        assertEquals("pc_101", capture.getProviderCaptureId());

        assertThrows(IllegalStateException.class,
            () -> authorization.requestCapture("capture-order-101-retry-with-new-key"));
        assertThrows(IllegalStateException.class,
            () -> authorization.failCapture("capture-order-101", "LATE_FAILURE"));
    }

    @Test
    void partialRefundsCannotExceedCapturedAmount() {
        Authorization authorization = captured("100.00");

        PaymentRefund first = authorization.requestRefund(
            new Money("40.00"), "item unavailable", "refund-order-101-a");
        authorization.completeRefund("refund-order-101-a", "pr_101_a");

        PaymentRefund second = authorization.requestRefund(
            new Money("60.00"), "order cancelled", "refund-order-101-b");

        assertEquals(AuthorizationStatus.CAPTURED, authorization.getStatus());
        assertEquals(new Money("40.00"), authorization.getRefundedAmount());
        assertEquals(new Money("0.00"), authorization.getRefundableAmount());
        assertEquals(FinancialOperationStatus.PENDING, second.getStatus());
        assertThrows(RefundExceedsCapturedAmountException.class,
            () -> authorization.requestRefund(
                new Money("0.01"), "over refund", "refund-order-101-c"));
    }

    @Test
    void duplicateRefundRequestReturnsOriginalOperation() {
        Authorization authorization = captured("100.00");

        PaymentRefund first = authorization.requestRefund(
            new Money("25.00"), "partial adjustment", "refund-order-101-a");
        PaymentRefund duplicate = authorization.requestRefund(
            new Money("25.00"), "partial adjustment", "refund-order-101-a");

        assertSame(first, duplicate);
        assertEquals(1, authorization.getPaymentRefunds().size());

        assertThrows(IllegalArgumentException.class,
            () -> authorization.requestRefund(
                new Money("30.00"), "different amount", "refund-order-101-a"));
    }

    @Test
    void refundCompletionIsMonotonic() {
        Authorization authorization = captured("100.00");
        PaymentRefund refund = authorization.requestRefund(
            new Money("25.00"), "partial adjustment", "refund-order-101-a");

        assertTrue(authorization.completeRefund("refund-order-101-a", "pr_101_a"));
        assertFalse(authorization.completeRefund("refund-order-101-a", "pr_101_a"));
        assertEquals(FinancialOperationStatus.SUCCEEDED, refund.getStatus());

        assertThrows(IllegalStateException.class,
            () -> authorization.failRefund("refund-order-101-a", "LATE_FAILURE"));
        assertThrows(IllegalStateException.class,
            () -> authorization.completeRefund("refund-order-101-a", "pr_other"));
    }

    @Test
    void fullRefundTransitionsAggregateAndReplaysMonotonically() {
        Authorization authorization = captured("100.00");
        authorization.requestRefund(
            new Money("100.00"), "order cancelled", "refund-order-101-full");

        assertTrue(authorization.completeRefund("refund-order-101-full", "pr_101_full"));
        assertEquals(AuthorizationStatus.REFUNDED, authorization.getStatus());
        assertEquals(new Money("100.00"), authorization.getRefundedAmount());
        assertFalse(authorization.completeRefund("refund-order-101-full", "pr_101_full"));
        assertThrows(IllegalStateException.class,
            () -> authorization.completeRefund("refund-order-101-full", "pr_other"));
    }

    private static Authorization authorized(String amount) {
        return new Authorization(101L, "authorize-order-101", new Money(amount));
    }

    private static Authorization captured(String amount) {
        Authorization authorization = authorized(amount);
        authorization.requestCapture("capture-order-101");
        authorization.completeCapture("capture-order-101", "pc_101");
        return authorization;
    }
}
