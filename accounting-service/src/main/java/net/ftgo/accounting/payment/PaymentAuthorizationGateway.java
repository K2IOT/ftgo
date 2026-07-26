package net.ftgo.accounting.payment;

import net.ftgo.common.Money;

/** Port for the external payment provider authorization decision. */
public interface PaymentAuthorizationGateway {

    PaymentAuthorizationDecision authorize(String paymentToken, Money amount);

    /**
     * New settlement-aware entrypoint. Legacy adapters can continue implementing
     * the two-argument method during a rolling upgrade.
     */
    default PaymentAuthorizationDecision authorize(
        String paymentToken,
        Money amount,
        String requestId
    ) {
        return authorize(paymentToken, amount);
    }
}
