package net.ftgo.order.saga;

/**
 * Serializable state for explicit restaurant rejection or acceptance timeout.
 */
public class RejectOrderSagaData {

    private Long orderId;
    private Long consumerId;
    private Long authorizationId;
    private Long creditReservationId;
    private String failureCode;
    private String failureMessage;

    public RejectOrderSagaData() {
    }

    public RejectOrderSagaData(
        Long orderId,
        Long consumerId,
        Long authorizationId,
        Long creditReservationId,
        String failureCode,
        String failureMessage
    ) {
        this.orderId = orderId;
        this.consumerId = consumerId;
        this.authorizationId = authorizationId;
        this.creditReservationId = creditReservationId;
        this.failureCode = failureCode;
        this.failureMessage = failureMessage;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public Long getConsumerId() {
        return consumerId;
    }

    public void setConsumerId(Long consumerId) {
        this.consumerId = consumerId;
    }

    public Long getAuthorizationId() {
        return authorizationId;
    }

    public void setAuthorizationId(Long authorizationId) {
        this.authorizationId = authorizationId;
    }

    public Long getCreditReservationId() {
        return creditReservationId;
    }

    public void setCreditReservationId(Long creditReservationId) {
        this.creditReservationId = creditReservationId;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public void setFailureCode(String failureCode) {
        this.failureCode = failureCode;
    }

    public String getFailureMessage() {
        return failureMessage;
    }

    public void setFailureMessage(String failureMessage) {
        this.failureMessage = failureMessage;
    }
}
