package net.ftgo.accounting.settlement;

public class SettlementRetryExhaustedException extends IllegalStateException {

    public SettlementRetryExhaustedException(
        String operation,
        int attempts,
        SettlementGatewayTimeoutException cause
    ) {
        super(
            "Settlement " + operation + " exhausted after " + attempts + " attempts",
            cause
        );
    }
}