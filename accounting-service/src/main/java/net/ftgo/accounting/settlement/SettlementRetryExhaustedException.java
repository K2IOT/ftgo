package net.ftgo.accounting.settlement;

/**
 * Terminal transient-provider failure after the configured finite retry budget.
 * It extends IllegalArgumentException so Eventuate participant handlers convert
 * it to a failure reply instead of throwing and leaving the saga without a reply.
 */
public class SettlementRetryExhaustedException extends IllegalArgumentException {

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