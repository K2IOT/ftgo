package net.ftgo.accounting.settlement;

public class SettlementGatewayTimeoutException extends RuntimeException {

    public SettlementGatewayTimeoutException(String message) {
        super(message);
    }
}