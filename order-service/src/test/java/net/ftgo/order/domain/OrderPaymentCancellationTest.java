package net.ftgo.order.domain;

import net.ftgo.common.Money;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderPaymentCancellationTest {

    @Test
    void cancelBeforeCaptureRequiresVoidBeforeTerminalCancel() {
        Order order = awaitingOrder();

        order.beginCancel();
        assertThat(order.getState()).isEqualTo(OrderState.CANCEL_PENDING);
        assertThrows(IllegalStateException.class, order::confirmCancel);

        assertTrue(order.markPaymentVoided("cancel-order-101-authorization-501-void"));
        assertFalse(order.markPaymentVoided("cancel-order-101-authorization-501-void"));
        order.confirmCancel();

        assertThat(order.getPaymentState()).isEqualTo(OrderPaymentState.VOIDED);
        assertThat(order.getState()).isEqualTo(OrderState.CANCELLED);
    }

    @Test
    void cancelAfterCaptureRequiresRefundBeforeTerminalCancel() {
        Order order = capturedOrder();

        order.beginCancel();
        assertThrows(IllegalStateException.class, order::confirmCancel);

        assertTrue(order.markPaymentRefunded("cancel-order-101-capture-801-refund"));
        assertFalse(order.markPaymentRefunded("cancel-order-101-capture-801-refund"));
        order.confirmCancel();

        assertThat(order.getPaymentState()).isEqualTo(OrderPaymentState.REFUNDED);
        assertThat(order.getState()).isEqualTo(OrderState.CANCELLED);
    }

    @Test
    void manualReviewPausesCancellation() {
        Order order = capturedOrder();
        ReflectionTestUtils.setField(order, "paymentState", OrderPaymentState.MANUAL_REVIEW);

        assertThrows(IllegalStateException.class, order::beginCancel);
        assertThat(order.getState()).isEqualTo(OrderState.APPROVED);
    }

    private Order awaitingOrder() {
        Order value = new Order(
            301L,
            202L,
            List.of(new OrderLineItem(11L, "Burger", new Money("25.00"), 1)),
            new DeliveryInfo("1 Main St, Hanoi, HN 10000", LocalDateTime.now().plusHours(1)),
            new PaymentInfo("tok_phase_02b")
        );
        ReflectionTestUtils.setField(value, "id", 101L);
        value.awaitRestaurantAcceptance(
            901L,
            501L,
            601L,
            LocalDateTime.now().plusMinutes(5)
        );
        return value;
    }

    private Order capturedOrder() {
        Order value = awaitingOrder();
        value.beginPaymentCapture("accept-ticket-901");
        value.completePaymentCapture(801L, "capture-order-101-authorization-501");
        value.confirmRestaurantAcceptance();
        return value;
    }
}
