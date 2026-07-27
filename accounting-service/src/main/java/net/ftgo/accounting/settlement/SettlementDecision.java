package net.ftgo.accounting.settlement;

public record SettlementDecision(
    boolean approved,
    String providerReference,
    String reason
) {
    public static SettlementDecision approved(String providerReference) {
        return new SettlementDecision(true, providerReference, null);
    }

    public static SettlementDecision denied(String reason) {
        return new SettlementDecision(false, null, reason);
    }
}