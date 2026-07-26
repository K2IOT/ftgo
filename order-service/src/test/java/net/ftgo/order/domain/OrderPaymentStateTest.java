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

class OrderPaymentStateTest {

    @Test
    void acceptanceRequestClaimsOneCaptureOperation() {
        Order order = awaitingOrder();

        assertTrue(order.beginPaymentCapture("accept-ticket-901"));
        assertFalse(order.beginPaymentCapture("accept-ticket-901"));

        assertThat(order.getState()).isEqualTo(OrderState.CONFIRMATION_PENDING);
        assertThat(order.getPaymentState()).isEqualTo(OrderPaymentState.CAPTURE_PENDING);
        assertThat(order.getPaymentOperationRequestId())
            .isEqualTo("capture-order-101-authorization-501");
        assertThat(order.getAcceptanceRequestId()).isEqualTo("accept-ticket-901");
        assertThrows(IllegalStateException.class,
            () -> order.beginPaymentCapture("different-acceptance"));
    }

    @Test
    void successfulCaptureIsMonotonic() {
        Order order = awaitingOrder();
        order.beginPaymentCapture("accept-ticket-901");

        assertTrue(order.completePaymentCapture(
            801L,
            "capture-order-101-authorization-501"
        ));
        assertFalse(order.completePaymentCapture(
            801L,
            "capture-order-101-authorization-501"
        ));

        assertThat(order.getPaymentState()).isEqualTo(OrderPaymentState.CAPTURED);
        assertThat(order.getCaptureId()).isEqualTo(801L);
        assertThrows(IllegalStateException.class,
            () -> order.failPaymentCapture("LATE_DECLINE"));
    }

    @Test
    void declinedCaptureMovesOrderToDurableRejection() {
        Order order = awaitingOrder();
        order.beginPaymentCapture("accept-ticket-901");

        assertTrue(order.failPaymentCapture("PAYMENT_CAPTURE_DECLINED"));
        assertFalse(order.failPaymentCapture("PAYMENT_CAPTURE_DECLINED"));

        assertThat(order.getPaymentState()).isEqualTo(OrderPaymentState.FAILED);
        assertThat(order.getState()).isEqualTo(OrderState.REJECTION_PENDING);
        assertThat(order.getRejectionCode()).isEqualTo("PAYMENT_CAPTURE_DECLINED");
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
}
