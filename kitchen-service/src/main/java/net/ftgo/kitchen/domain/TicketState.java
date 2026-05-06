package net.ftgo.kitchen.domain;

/**
 * State enum for Ticket aggregate state machine.
 * 
 * Valid state transitions:
 * CREATE_PENDING → AWAITING_ACCEPTANCE (via approve())
 * AWAITING_ACCEPTANCE → ACCEPTED (via accept())
 * ACCEPTED → PREPARING (via preparing())
 * PREPARING → READY_FOR_PICKUP (via readyForPickup())
 * READY_FOR_PICKUP → PICKED_UP (via pickedUp())
 * Any state → CANCEL_PENDING (via beginCancel()) → CANCELLED (via confirmCancel())
 * Any state → REVISION_PENDING (via beginRevise()) → restored state (via confirmRevise())
 * CANCEL_PENDING → previous state (via undoCancel(), compensation)
 * REVISION_PENDING → previous state (via undoRevise(), compensation)
 */
public enum TicketState {
    /**
     * Initial state when ticket is created during CreateOrderSaga.
     * Waiting for saga to approve the ticket.
     */
    CREATE_PENDING,
    
    /**
     * Ticket has been approved by saga and is waiting for kitchen staff to accept.
     */
    AWAITING_ACCEPTANCE,
    
    /**
     * Kitchen staff has accepted the ticket and will begin preparation.
     */
    ACCEPTED,
    
    /**
     * Kitchen is actively preparing the order.
     */
    PREPARING,
    
    /**
     * Order is ready for courier pickup.
     */
    READY_FOR_PICKUP,
    
    /**
     * Courier has picked up the order.
     */
    PICKED_UP,
    
    /**
     * Ticket cancellation is in progress (CancelOrderSaga active).
     * Semantic lock — prevents concurrent modifications.
     */
    CANCEL_PENDING,
    
    /**
     * Ticket revision is in progress (ReviseOrderSaga active).
     * Semantic lock — prevents concurrent modifications.
     */
    REVISION_PENDING,
    
    /**
     * Ticket has been cancelled (compensation from saga).
     */
    CANCELLED
}
