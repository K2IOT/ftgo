package net.ftgo.accounting.reconciliation;

public enum PaymentReconciliationResult {
    SAFE_UPDATE,
    NO_CHANGE,
    MANUAL_REVIEW,
    CRITICAL_CASE,
    UNKNOWN_LOCAL_AUTHORIZATION,
    PROVIDER_NOT_FOUND
}
