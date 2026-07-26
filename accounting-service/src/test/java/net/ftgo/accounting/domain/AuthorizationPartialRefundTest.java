package net.ftgo.accounting.domain;

import net.ftgo.common.Money;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthorizationPartialRefundTest {

    @Test
    void supportsMultiplePartialRefundsUntilFullyRefunded() {
        Authorization authorization = capturedAuthorization("100000");

        assertThat(authorization.refund(new Money("30000"), "item unavailable", "refund-1")).isTrue();
        assertThat(authorization.getStatus()).isEqualTo(AuthorizationStatus.PARTIALLY_REFUNDED);
        assertThat(authorization.getRefundedAmount()).isEqualTo(new Money("30000"));
        assertThat(authorization.getRefundableAmount()).isEqualTo(new Money("70000"));

        assertThat(authorization.refund(new Money("70000"), "order cancelled", "refund-2")).isTrue();
        assertThat(authorization.getStatus()).isEqualTo(AuthorizationStatus.REFUNDED);
        assertThat(authorization.getRefundedAmount()).isEqualTo(new Money("100000"));
        assertThat(authorization.getRefunds()).hasSize(2);
    }

    @Test
    void duplicateRequestIsIdempotentButConflictingAmountIsRejected() {
        Authorization authorization = capturedAuthorization("100000");

        assertThat(authorization.refund(new Money("25000"), "adjustment", "refund-1")).isTrue();
        assertThat(authorization.refund(new Money("25000"), "adjustment", "refund-1")).isFalse();
        assertThat(authorization.getRefunds()).hasSize(1);

        assertThatThrownBy(() -> authorization.refund(
            new Money("20000"), "adjustment", "refund-1"
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("reused");
    }

    @Test
    void rejectsOverRefund() {
        Authorization authorization = capturedAuthorization("100000");
        authorization.refund(new Money("60000"), "partial", "refund-1");

        assertThatThrownBy(() -> authorization.refund(
            new Money("50000"), "too much", "refund-2"
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("exceeds captured amount");
    }

    private Authorization capturedAuthorization(String amount) {
        Authorization authorization = new Authorization(42L, "authorize-1", new Money(amount));
        authorization.capture("capture-1");
        return authorization;
    }
}
