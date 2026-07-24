package net.ftgo.kitchen.domain;

/**
 * State machine for a kitchen ticket.
 *
 * <p>Restaurant acceptance has exactly one terminal decision:
 * ACCEPTED, REJECTED_BY_RESTAURANT, or REJECTED_TIMEOUT.</p>
 */
public enum TicketState {
    CREATE_PENDING,
    AWAITING_ACCEPTANCE,
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
