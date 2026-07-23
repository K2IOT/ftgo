package net.ftgo.accounting.domain;

public enum AuthorizationStatus {
    AUTHORIZED,
    DENIED,
    CAPTURED,
    VOIDED,
    REFUNDED,

    /** Legacy states retained while old Cancel/Revise sagas are migrated. */
    APPROVED,
    REVERSED
}
