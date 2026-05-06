package net.ftgo.order.saga;

import net.ftgo.common.Money;
import net.ftgo.order.domain.OrderLineItem;

import java.util.List;

/**
 * Saga data for CreateOrderSaga.
 * 
 * This class holds all the state needed throughout the saga execution,
 * including data passed between saga steps and identifiers for created resources.
 * 
 * The saga data is persisted in the saga_instance table in MySQL, allowing
 * the saga to resume after service restarts or failures.
 * 
 * Saga Flow:
 * 1. createOrder (local) - creates order in APPROVAL_PENDING state
 * 2. verifyConsumer - validates consumer credit limit
 * 3. createTicket - creates kitchen ticket
 * 4. authorizeCard - authorizes payment (PIVOT POINT)
 * 5. approveTicket - approves kitchen ticket (retriable)
 * 6. approveOrder (local) - transitions order to APPROVED state (retriable)
 */
public class CreateOrderSagaData {
    
    private Long orderId;
    private Long consumerId;
    private Long restaurantId;
    private List<OrderLineItem> lineItems;
    private Money orderTotal;
    
    // IDs of created resources (populated during saga execution)
    private Long ticketId;
    private Long authorizationId;
    
    /**
     * Default constructor for serialization.
     */
    public CreateOrderSagaData() {
    }
    
    /**
     * Creates saga data for a new order.
     * 
     * @param orderId the order ID
     * @param consumerId the consumer ID
     * @param restaurantId the restaurant ID
     * @param lineItems the order line items
     * @param orderTotal the order total amount
     */
    public CreateOrderSagaData(Long orderId, Long consumerId, Long restaurantId,
                               List<OrderLineItem> lineItems, Money orderTotal) {
        this.orderId = orderId;
        this.consumerId = consumerId;
        this.restaurantId = restaurantId;
        this.lineItems = lineItems;
        this.orderTotal = orderTotal;
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
    
    public Long getRestaurantId() {
        return restaurantId;
    }
    
    public void setRestaurantId(Long restaurantId) {
        this.restaurantId = restaurantId;
    }
    
    public List<OrderLineItem> getLineItems() {
        return lineItems;
    }
    
    public void setLineItems(List<OrderLineItem> lineItems) {
        this.lineItems = lineItems;
    }
    
    public Money getOrderTotal() {
        return orderTotal;
    }
    
    public void setOrderTotal(Money orderTotal) {
        this.orderTotal = orderTotal;
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
        return String.format("CreateOrderSagaData{orderId=%d, consumerId=%d, restaurantId=%d, orderTotal=%s, ticketId=%d, authorizationId=%d}",
            orderId, consumerId, restaurantId, orderTotal, ticketId, authorizationId);
    }
}
