package net.ftgo.orderhistory.messaging;

/**
 * Domain event published when a kitchen ticket is accepted.
 * 
 * Published by Kitchen Service to net.ftgo.kitchenservice.domain.Ticket topic.
 */
public class TicketAcceptedEvent {
    
    private Long ticketId;
    private Long orderId;
    
    /**
     * Default constructor for JSON deserialization.
     */
    public TicketAcceptedEvent() {
    }
    
    public Long getTicketId() {
        return ticketId;
    }
    
    public void setTicketId(Long ticketId) {
        this.ticketId = ticketId;
    }
    
    public Long getOrderId() {
        return orderId;
    }
    
    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }
}
