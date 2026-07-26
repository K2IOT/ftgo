package net.ftgo.kitchen.domain;

/** State machine for a kitchen ticket. */
public enum TicketState {
    CREATE_PENDING,
    AWAITING_ACCEPTANCE,
    ACCEPTANCE_PENDING_PAYMENT,
    ACCEPTED,
    REJECTED_BY_RESTAURANT,
    REJECTED_TIMEOUT,
    PREPARING,
    READY_FOR_PICKUP,
    PICKED_UP,
    CANCEL_PENDING,
    REVISION_PENDING,
    CANCELLED
}
