package net.ftgo.accounting.payment;

import net.ftgo.common.Money;

/** External provider boundary for the complete payment settlement lifecycle. */
public interface PaymentProvider extends PaymentAuthorizationGateway {

    PaymentProviderResult capture(
        String providerAuthorizationId,
        Money amount,
        String requestId
    );

    PaymentProviderResult voidAuthorization(
        String providerAuthorizationId,
        String requestId
    );

    PaymentProviderResult refund(
        String providerCaptureId,
        Money amount,
        String requestId
    );
}
