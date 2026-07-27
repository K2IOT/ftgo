package net.ftgo.accounting.settlement;

import net.ftgo.common.Money;

import java.util.Optional;

public interface SettlementGateway {

    SettlementDecision authorize(
        Long authorizationId,
        Long orderId,
        Money amount,
        String requestId
    );

    SettlementDecision capture(Long authorizationId, Long orderId, String requestId);

    SettlementDecision voidAuthorization(Long authorizationId, Long orderId, String requestId);

    SettlementDecision refund(
        Long authorizationId,
        Long orderId,
        Money amount,
        String requestId
    );

    Optional<ProviderSettlementSnapshot> find(Long authorizationId);

    SettlementDecision synchronize(SettlementTarget target, String requestId);
}