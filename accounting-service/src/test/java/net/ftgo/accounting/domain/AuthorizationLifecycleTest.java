package net.ftgo.accounting.domain;

import net.ftgo.common.Money;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthorizationLifecycleTest {

    @Test
    void authorizationCanBeCapturedIdempotently() {
        Authorization authorization = authorized(101L, "order-101-authorize");

        authorization.capture("order-101-capture");
        authorization.capture("order-101-capture");

        assertThat(authorization.getStatus()).isEqualTo(AuthorizationStatus.CAPTURED);
        assertThat(authorization.getCaptureRequestId()).isEqualTo("order-101-capture");
        assertThat(authorization.getCapturedAt()).isNotNull();
    }

    @Test
    void aDifferentSecondCaptureRequestIsRejected() {
        Authorization authorization = authorized(101L, "order-101-authorize");
        authorization.capture("order-101-capture");

        assertThatThrownBy(() -> authorization.capture("order-101-capture-duplicate"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("already captured");
    }

    @Test
    void authorizationCanBeVoidedIdempotentlyBeforeCapture() {
        Authorization authorization = authorized(101L, "order-101-authorize");

        authorization.voidAuthorization("RESTAURANT_TIMEOUT", "order-101-void");
        authorization.voidAuthorization("RESTAURANT_TIMEOUT", "order-101-void");

        assertThat(authorization.getStatus()).isEqualTo(AuthorizationStatus.VOIDED);
        assertThat(authorization.getVoidRequestId()).isEqualTo("order-101-void");
        assertThat(authorization.getVoidedAt()).isNotNull();
    }

    @Test
    void captureAfterVoidAndVoidAfterCaptureAreRejected() {
        Authorization voided = authorized(101L, "authorize-voided");
        voided.voidAuthorization("REJECTED", "void-1");
        assertThatThrownBy(() -> voided.capture("capture-1"))
            .isInstanceOf(IllegalStateException.class);

        Authorization captured = authorized(102L, "authorize-captured");
        captured.capture("capture-2");
        assertThatThrownBy(() -> captured.voidAuthorization("REJECTED", "void-2"))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void capturedPaymentCanBeRefundedIdempotently() {
        Authorization authorization = authorized(101L, "order-101-authorize");
        authorization.capture("order-101-capture");

        authorization.refund(new Money("25.00"), "ORDER_CANCELLED", "order-101-refund-1");
        authorization.refund(new Money("25.00"), "ORDER_CANCELLED", "order-101-refund-1");

        assertThat(authorization.getStatus()).isEqualTo(AuthorizationStatus.REFUNDED);
        assertThat(authorization.getRefundRequestId()).isEqualTo("order-101-refund-1");
        assertThat(authorization.getRefundedAt()).isNotNull();
    }

    @Test
    void partialOrMismatchedRefundIsRejected() {
        Authorization authorization = authorized(101L, "order-101-authorize");
        authorization.capture("order-101-capture");

        assertThatThrownBy(() -> authorization.refund(
            new Money("10.00"), "PARTIAL", "order-101-refund-1"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("full authorization amount");
    }

    private Authorization authorized(Long orderId, String requestId) {
        Authorization authorization = new Authorization(
            301L,
            orderId,
            requestId,
            new Money("25.00"),
            AuthorizationStatus.AUTHORIZED
        );
        ReflectionTestUtils.setField(authorization, "id", orderId + 400L);
        return authorization;
    }
}
