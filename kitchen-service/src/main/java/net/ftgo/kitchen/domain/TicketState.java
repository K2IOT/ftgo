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
 * Any state → CANCELLED (via cancel())
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
     * Ticket has been cancelled (compensation from saga).
     */
    CANCELLED
}
