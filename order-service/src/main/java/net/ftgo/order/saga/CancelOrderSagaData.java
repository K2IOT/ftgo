package net.ftgo.order.saga;

import net.ftgo.common.Money;
import net.ftgo.order.domain.OrderPaymentState;

/** Serializable state for cancellation and financial settlement. */
public class CancelOrderSagaData {

    private Long orderId;
    private Long consumerId;
    private Long ticketId;
    private Long authorizationId;
    private Long captureId;
    private Money orderTotal;
    private OrderPaymentState paymentState;

    public CancelOrderSagaData() {
    }

    /** Rolling compatibility for in-flight Phase 02 saga callers. */
    public CancelOrderSagaData(
        Long orderId,
        Long consumerId,
        Long ticketId,
        Long authorizationId
    ) {
        this(
            orderId,
            consumerId,
            ticketId,
            authorizationId,
            null,
            Money.ZERO,
            OrderPaymentState.AUTHORIZED
        );
    }

    public CancelOrderSagaData(
        Long orderId,
        Long consumerId,
        Long ticketId,
        Long authorizationId,
        Long captureId,
        Money orderTotal,
        OrderPaymentState paymentState
    ) {
        this.orderId = orderId;
        this.consumerId = consumerId;
        this.ticketId = ticketId;
        this.authorizationId = authorizationId;
        this.captureId = captureId;
        this.orderTotal = orderTotal;
        this.paymentState = paymentState;
    }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getConsumerId() { return consumerId; }
    public void setConsumerId(Long consumerId) { this.consumerId = consumerId; }
    public Long getTicketId() { return ticketId; }
    public void setTicketId(Long ticketId) { this.ticketId = ticketId; }
    public Long getAuthorizationId() { return authorizationId; }
    public void setAuthorizationId(Long authorizationId) {
        this.authorizationId = authorizationId;
    }
    public Long getCaptureId() { return captureId; }
    public void setCaptureId(Long captureId) { this.captureId = captureId; }
    public Money getOrderTotal() { return orderTotal; }
    public void setOrderTotal(Money orderTotal) { this.orderTotal = orderTotal; }
    public OrderPaymentState getPaymentState() { return paymentState; }
    public void setPaymentState(OrderPaymentState paymentState) {
        this.paymentState = paymentState;
    }

    @Override
    public String toString() {
        return "CancelOrderSagaData{orderId=" + orderId
            + ", consumerId=" + consumerId
            + ", ticketId=" + ticketId
            + ", authorizationId=" + authorizationId
            + ", captureId=" + captureId
            + ", paymentState=" + paymentState + "}";
    }
}
