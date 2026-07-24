package net.ftgo.common.orderflow.replies;

import net.ftgo.common.Money;

public class ConsumerCreditReserved {

    private Long reservationId;
    private Long orderId;
    private Money amount;

    public ConsumerCreditReserved() {
    }

    public ConsumerCreditReserved(Long reservationId, Long orderId, Money amount) {
        this.reservationId = reservationId;
        this.orderId = orderId;
        this.amount = amount;
    }

    public Long getReservationId() { return reservationId; }
    public void setReservationId(Long reservationId) { this.reservationId = reservationId; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Money getAmount() { return amount; }
    public void setAmount(Money amount) { this.amount = amount; }
}
