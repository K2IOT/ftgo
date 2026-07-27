package net.ftgo.accounting.settlement;

public record SettlementReconciliationReport(
    int inspected,
    int detected,
    int resolved
) {
}