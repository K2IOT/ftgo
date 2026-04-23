package net.ftgo.delivery.domain;

/**
 * Status enum for Delivery aggregate state machine.
 * 
 * Valid state transitions:
 * PENDING → ASSIGNED (via assignCourier())
 * ASSIGNED → PICKED_UP (via pickup())
 * PICKED_UP → DELIVERED (via deliver())
 */
public enum DeliveryStatus {
    /**
     * Initial state when delivery is created from OrderApproved event.
     * Waiting for courier assignment.
     */
    PENDING,
    
    /**
     * Courier has been assigned to the delivery.
     */
    ASSIGNED,
    
    /**
     * Courier has picked up the order from the restaurant.
     */
    PICKED_UP,
    
    /**
     * Order has been delivered to the consumer.
     */
    DELIVERED
}
