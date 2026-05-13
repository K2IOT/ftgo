package net.ftgo.order.saga;

import net.ftgo.common.Money;
import net.ftgo.order.domain.OrderLineItem;

import java.util.List;

/**
 * Saga data for ReviseOrderSaga.
 * 
 * This class holds all the state needed throughout the saga execution,
 * including the revised order details and identifiers for resources that need to be updated.
 * 
 * The saga data is persisted in the saga_instance table in MySQL, allowing
 * the saga to resume after service restarts or failures.
 * 
 * Saga Flow:
 * 1. beginRevise (local) - transitions order to REVISION_PENDING state
 * 2. beginReviseTicket - updates ticket with revised line items
 * 3. reviseCreditCardAuthorization - adjusts payment authorization (PIVOT POINT)
 * 4. confirmReviseTicket - confirms ticket revision (retriable)
 * 5. confirmRevise (local) - updates order and transitions to APPROVED state (retriable)
 */
public class ReviseOrderSagaData {
    
    private Long orderId;
    private Long consumerId;
    private List<OrderLineItem> revisedLineItems;
    private Money revisedTotal;
    
    // IDs of resources to update (populated from existing order)
    private Long ticketId;
    private Long authorizationId;
    private Long revisedAuthorizationId;
    
    /**
     * Default constructor for serialization.
     */
    public ReviseOrderSagaData() {
    }
    
    /**
     * Creates saga data for order revision.
     * 
     * @param orderId the order ID to revise
     * @param consumerId the consumer ID (needed for accounting commands)
     * @param revisedLineItems the new line items
     * @param revisedTotal the new order total
     * @param ticketId the ticket ID to update
     * @param authorizationId the authorization ID to revise
     */
    public ReviseOrderSagaData(Long orderId, Long consumerId, List<OrderLineItem> revisedLineItems, 
                               Money revisedTotal, Long ticketId, Long authorizationId) {
        this.orderId = orderId;
        this.consumerId = consumerId;
        this.revisedLineItems = revisedLineItems;
        this.revisedTotal = revisedTotal;
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
    
    public List<OrderLineItem> getRevisedLineItems() {
        return revisedLineItems;
    }
    
    public void setRevisedLineItems(List<OrderLineItem> revisedLineItems) {
        this.revisedLineItems = revisedLineItems;
    }
    
    public Money getRevisedTotal() {
        return revisedTotal;
    }
    
    public void setRevisedTotal(Money revisedTotal) {
        this.revisedTotal = revisedTotal;
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

    public Long getRevisedAuthorizationId() {
        return revisedAuthorizationId;
    }

    public void setRevisedAuthorizationId(Long revisedAuthorizationId) {
        this.revisedAuthorizationId = revisedAuthorizationId;
    }

    public Long getCurrentAuthorizationId() {
        return revisedAuthorizationId != null ? revisedAuthorizationId : authorizationId;
    }
    
    @Override
    public String toString() {
        return String.format("ReviseOrderSagaData{orderId=%d, consumerId=%d, revisedTotal=%s, ticketId=%d, authorizationId=%d, revisedAuthorizationId=%d}",
            orderId, consumerId, revisedTotal, ticketId, authorizationId, revisedAuthorizationId);
    }
}
