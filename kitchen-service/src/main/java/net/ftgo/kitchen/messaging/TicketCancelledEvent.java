package net.ftgo.kitchen.messaging;

/**
 * Domain event published when a ticket is cancelled.
 */
public class TicketCancelledEvent {
    
    private Long ticketId;
    private Long orderId;
    
    public TicketCancelledEvent() {
    }
    
    public TicketCancelledEvent(Long ticketId, Long orderId) {
        this.ticketId = ticketId;
        this.orderId = orderId;
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
