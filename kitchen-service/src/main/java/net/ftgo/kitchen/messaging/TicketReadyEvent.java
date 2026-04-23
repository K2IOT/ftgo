package net.ftgo.kitchen.messaging;

import java.time.LocalDateTime;

/**
 * Domain event published when a ticket is ready for pickup.
 */
public class TicketReadyEvent {
    
    private Long ticketId;
    private Long orderId;
    private LocalDateTime readyAt;
    
    public TicketReadyEvent() {
    }
    
    public TicketReadyEvent(Long ticketId, Long orderId, LocalDateTime readyAt) {
        this.ticketId = ticketId;
        this.orderId = orderId;
        this.readyAt = readyAt;
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
    
    public LocalDateTime getReadyAt() {
        return readyAt;
    }
    
    public void setReadyAt(LocalDateTime readyAt) {
        this.readyAt = readyAt;
    }
}
