package net.ftgo.consumer.service;

public class CreditReservationException extends RuntimeException {

    public static final String CONSUMER_NOT_FOUND = "CONSUMER_NOT_FOUND";
    public static final String INSUFFICIENT_CREDIT = "INSUFFICIENT_CREDIT";
    public static final String RESERVATION_NOT_FOUND = "RESERVATION_NOT_FOUND";
    public static final String RESERVATION_CONFLICT = "RESERVATION_CONFLICT";
    public static final String INVALID_RESERVATION_STATE = "INVALID_RESERVATION_STATE";

    private final String reasonCode;

    public CreditReservationException(String reasonCode, String message) {
        super(message);
        this.reasonCode = reasonCode;
    }

    public String getReasonCode() {
        return reasonCode;
    }
}
