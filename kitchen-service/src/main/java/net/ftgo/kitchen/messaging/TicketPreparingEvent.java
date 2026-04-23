package net.ftgo.kitchen.messaging;

/**
 * Domain event published when a ticket enters preparing state.
 */
public class TicketPreparingEvent {
    
    private Long ticketId;
    private Long orderId;
    
    public TicketPreparingEvent() {
    }
    
    public TicketPreparingEvent(Long ticketId, Long orderId) {
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
