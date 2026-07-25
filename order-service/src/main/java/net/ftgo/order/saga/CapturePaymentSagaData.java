package net.ftgo.order.saga;

/** Serializable state for the restaurant-acceptance payment capture saga. */
public class CapturePaymentSagaData {

    private Long orderId;
    private Long consumerId;
    private Long ticketId;
    private Long authorizationId;
    private Long creditReservationId;
    private String acceptanceRequestId;
    private Long captureId;
    private String failureCode;

    public CapturePaymentSagaData() {
    }

    public CapturePaymentSagaData(
        Long orderId,
        Long consumerId,
        Long ticketId,
        Long authorizationId,
        Long creditReservationId,
        String acceptanceRequestId
    ) {
        this.orderId = orderId;
        this.consumerId = consumerId;
        this.ticketId = ticketId;
        this.authorizationId = authorizationId;
        this.creditReservationId = creditReservationId;
        this.acceptanceRequestId = acceptanceRequestId;
    }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getConsumerId() { return consumerId; }
    public void setConsumerId(Long consumerId) { this.consumerId = consumerId; }
    public Long getTicketId() { return ticketId; }
    public void setTicketId(Long ticketId) { this.ticketId = ticketId; }
    public Long getAuthorizationId() { return authorizationId; }
    public void setAuthorizationId(Long authorizationId) { this.authorizationId = authorizationId; }
    public Long getCreditReservationId() { return creditReservationId; }
    public void setCreditReservationId(Long creditReservationId) {
        this.creditReservationId = creditReservationId;
    }
    public String getAcceptanceRequestId() { return acceptanceRequestId; }
    public void setAcceptanceRequestId(String acceptanceRequestId) {
        this.acceptanceRequestId = acceptanceRequestId;
    }
    public Long getCaptureId() { return captureId; }
    public void setCaptureId(Long captureId) { this.captureId = captureId; }
    public String getFailureCode() { return failureCode; }
    public void setFailureCode(String failureCode) { this.failureCode = failureCode; }
}
