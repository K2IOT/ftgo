package net.ftgo.accounting.reconciliation;

public enum PaymentReconciliationCaseType {
    AMOUNT_MISMATCH,
    UNKNOWN_PROVIDER_CHARGE,
    PROVIDER_STATE_CONFLICT,
    PROVIDER_AUTHORIZATION_NOT_FOUND
}
