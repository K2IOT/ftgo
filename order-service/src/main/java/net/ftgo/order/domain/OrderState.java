package net.ftgo.order.domain;

/**
 * Order lifecycle with semantic-lock states for each saga boundary.
 */
public enum OrderState {
    /** Initial CreateOrderSaga is validating and reserving resources. */
    APPROVAL_PENDING,

    /** CreateOrderSaga completed and is waiting for a kitchen decision. */
    AWAITING_RESTAURANT_ACCEPTANCE,

    /** The acceptance event won the order-level decision race. */
    CONFIRMATION_PENDING,

    /** The rejection or timeout event won the order-level decision race. */
    REJECTION_PENDING,

    /** Payment is captured, credit is committed, and fulfillment may proceed. */
    APPROVED,

    /** Order creation or restaurant decision ended in rejection. */
    REJECTED,

    /** Existing cancellation workflow semantic lock. */
    CANCEL_PENDING,

    /** Terminal cancelled state. */
    CANCELLED,

    /** Existing revision workflow semantic lock. */
    REVISION_PENDING
}
