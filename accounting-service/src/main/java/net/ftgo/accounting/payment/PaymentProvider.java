package net.ftgo.accounting.payment;

import net.ftgo.common.Money;

import java.time.Instant;
import java.util.List;

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

    default PaymentProviderSettlementSnapshot getSettlement(
        String providerAuthorizationId
    ) {
        return PaymentProviderSettlementSnapshot.notFound(providerAuthorizationId);
    }

    default List<PaymentProviderCharge> listRecentCharges(Instant since) {
        return List.of();
    }
}
