package net.ftgo.order.domain;

/** Durable financial state used by capture, cancellation, and reconciliation. */
public enum OrderPaymentState {
    AUTHORIZED,
    CAPTURE_PENDING,
    CAPTURED,
    VOIDED,
    REFUND_PENDING,
    REFUNDED,
    FAILED,
    MANUAL_REVIEW
}
