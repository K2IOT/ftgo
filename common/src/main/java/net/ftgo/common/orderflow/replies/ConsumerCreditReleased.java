package net.ftgo.common.orderflow.replies;

public class ConsumerCreditReleased {

    private Long reservationId;
    private Long orderId;

    public ConsumerCreditReleased() {
    }

    public ConsumerCreditReleased(Long reservationId, Long orderId) {
        this.reservationId = reservationId;
        this.orderId = orderId;
    }

    public Long getReservationId() { return reservationId; }
    public void setReservationId(Long reservationId) { this.reservationId = reservationId; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
}
