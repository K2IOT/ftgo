package net.ftgo.accounting.domain;

public enum AuthorizationStatus {
    AUTHORIZED,
    DENIED,
    CAPTURED,
    PARTIALLY_REFUNDED,
    VOIDED,
    REFUNDED,

    /** Legacy states retained while old Cancel/Revise sagas are migrated. */
    APPROVED,
    REVERSED
}
