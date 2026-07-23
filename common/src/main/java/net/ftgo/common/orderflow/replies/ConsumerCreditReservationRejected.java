package net.ftgo.common.orderflow.replies;

public class ConsumerCreditReservationRejected {

    public static final String CONSUMER_NOT_FOUND = "CONSUMER_NOT_FOUND";
    public static final String INSUFFICIENT_CREDIT = "INSUFFICIENT_CREDIT";
    public static final String RESERVATION_CONFLICT = "RESERVATION_CONFLICT";

    private Long orderId;
    private String reasonCode;
    private String message;

    public ConsumerCreditReservationRejected() {
    }

    public ConsumerCreditReservationRejected(Long orderId, String reasonCode, String message) {
        this.orderId = orderId;
        this.reasonCode = reasonCode;
        this.message = message;
    }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public String getReasonCode() { return reasonCode; }
    public void setReasonCode(String reasonCode) { this.reasonCode = reasonCode; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
