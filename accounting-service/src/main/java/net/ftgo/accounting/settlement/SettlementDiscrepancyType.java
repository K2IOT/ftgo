package net.ftgo.accounting.settlement;

public enum SettlementDiscrepancyType {
    PROVIDER_MISSING,
    STATUS_MISMATCH,
    CAPTURE_AMOUNT_MISMATCH,
    REFUND_AMOUNT_MISMATCH,
    LEDGER_MISMATCH
}