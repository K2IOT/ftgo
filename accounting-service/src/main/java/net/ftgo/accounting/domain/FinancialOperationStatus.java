package net.ftgo.accounting.domain;

/** Monotonic lifecycle for provider-backed capture and refund operations. */
public enum FinancialOperationStatus {
    PENDING,
    SUCCEEDED,
    FAILED
}
