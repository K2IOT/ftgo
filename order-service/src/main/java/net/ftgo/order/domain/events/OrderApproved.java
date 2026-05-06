package net.ftgo.order.domain.events;

import net.ftgo.common.Money;

/**
 * Domain event published when an order is approved.
 * 
 * Published by CreateOrderSaga when the saga completes successfully.
 * Consumed by:
 * - Delivery Service: Creates delivery record
 * - Order History Service: Updates order status to APPROVED
 */
public class OrderApproved {
    
    private Long orderId;
    private Long consumerId;
    private Long restaurantId;
    private Money orderTotal;
    private Long ticketId;
    private Long authorizationId;
    
    /**
     * Default constructor for serialization.
     */
    public OrderApproved() {
    }
    
    /**
     * Creates an OrderApproved event.
     * 
     * @param orderId the order ID
     * @param consumerId the consumer ID
     * @param restaurantId the restaurant ID
     * @param orderTotal the order total
     * @param ticketId the kitchen ticket ID
     * @param authorizationId the payment authorization ID
     */
    public OrderApproved(Long orderId, Long consumerId, Long restaurantId, Money orderTotal,
                        Long ticketId, Long authorizationId) {
        this.orderId = orderId;
        this.consumerId = consumerId;
        this.restaurantId = restaurantId;
        this.orderTotal = orderTotal;
        this.ticketId = ticketId;
        this.authorizationId = authorizationId;
    }
    
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
}
