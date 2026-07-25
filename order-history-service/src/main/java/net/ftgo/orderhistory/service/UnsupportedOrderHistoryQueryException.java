package net.ftgo.orderhistory.service;

public class UnsupportedOrderHistoryQueryException extends RuntimeException {

    private final String errorCode;

    public UnsupportedOrderHistoryQueryException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
