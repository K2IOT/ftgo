package net.ftgo.accounting.payment;

import net.ftgo.common.Money;

/**
 * Port for the external payment provider authorization decision.
 */
public interface PaymentAuthorizationGateway {

    PaymentAuthorizationDecision authorize(String paymentToken, Money amount);
}
