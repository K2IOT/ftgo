package net.ftgo.order.saga;

/**
 * Saga data for CancelOrderSaga.
 * 
 * This class holds all the state needed throughout the saga execution,
 * including identifiers for the order and related resources that need to be cancelled.
 * 
 * The saga data is persisted in the saga_instance table in MySQL, allowing
 * the saga to resume after service restarts or failures.
 * 
 * Saga Flow:
 * 1. beginCancel (local) - transitions order to CANCEL_PENDING state
 * 2. beginCancelTicket - initiates ticket cancellation in Kitchen Service
 * 3. reverseAuthorization - reverses payment authorization (PIVOT POINT)
 * 4. confirmCancelTicket - confirms ticket cancellation (retriable)
 * 5. confirmCancel (local) - transitions order to CANCELLED state (retriable)
 */
public class CancelOrderSagaData {
    
    private Long orderId;
    private Long consumerId;
    private Long ticketId;
    private Long authorizationId;
    
    /**
     * Default constructor for serialization.
     */
    public CancelOrderSagaData() {
    }
    
    /**
     * Creates saga data for order cancellation.
     * 
     * @param orderId the order ID to cancel
     * @param consumerId the consumer ID (needed for accounting commands)
     * @param ticketId the ticket ID to cancel
     * @param authorizationId the authorization ID to reverse
     */
    public CancelOrderSagaData(Long orderId, Long consumerId, Long ticketId, Long authorizationId) {
        this.orderId = orderId;
        this.consumerId = consumerId;
        this.ticketId = ticketId;
        this.authorizationId = authorizationId;
    }
    
    // Getters and setters
    
    public Long getOrderId() {
        return orderId;
    }
    
    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }
    
    public Long getConsumerId() {
        return consumerId;
    }
    
    public void setConsumerId(Long consumerId) {
        this.consumerId = consumerId;
    }
    
    public Long getTicketId() {
        return ticketId;
    }
    
    public void setTicketId(Long ticketId) {
        this.ticketId = ticketId;
    }
    
    public Long getAuthorizationId() {
        return authorizationId;
    }
    
    public void setAuthorizationId(Long authorizationId) {
        this.authorizationId = authorizationId;
    }
    
    @Override
    public String toString() {
        return String.format("CancelOrderSagaData{orderId=%d, consumerId=%d, ticketId=%d, authorizationId=%d}",
            orderId, consumerId, ticketId, authorizationId);
    }
}
