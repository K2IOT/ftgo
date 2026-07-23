package net.ftgo.consumer.domain;

public class CreditLimitBelowReservedAmountException extends IllegalArgumentException {

    public CreditLimitBelowReservedAmountException() {
        super("Credit limit cannot be reduced below the reserved amount");
    }
}
