package net.ftgo.restaurant.service;

public class OrderMenuValidationException extends RuntimeException {

    private final String reasonCode;

    public OrderMenuValidationException(String reasonCode, String message) {
        super(message);
        this.reasonCode = reasonCode;
    }

    public String getReasonCode() {
        return reasonCode;
    }
}
