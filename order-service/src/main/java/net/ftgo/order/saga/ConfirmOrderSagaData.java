package net.ftgo.order.saga;

/**
 * Serializable state for the post-acceptance confirmation saga.
 */
public class ConfirmOrderSagaData {

    private Long orderId;
    private Long consumerId;
    private Long authorizationId;
    private Long creditReservationId;

    public ConfirmOrderSagaData() {
    }

    public ConfirmOrderSagaData(
        Long orderId,
        Long consumerId,
        Long authorizationId,
        Long creditReservationId
    ) {
        this.orderId = orderId;
        this.consumerId = consumerId;
        this.authorizationId = authorizationId;
        this.creditReservationId = creditReservationId;
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
}
