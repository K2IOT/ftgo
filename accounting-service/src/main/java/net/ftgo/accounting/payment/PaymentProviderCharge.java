package net.ftgo.accounting.payment;

import net.ftgo.common.Money;

import java.time.Instant;

public record PaymentProviderCharge(
    String providerChargeId,
    String providerAuthorizationId,
    Money amount,
    Instant occurredAt
) {
}
