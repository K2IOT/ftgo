package net.ftgo.kitchen.messaging;

import java.time.LocalDateTime;

/**
 * Domain event published when a ticket is accepted by kitchen staff.
 */
public class TicketAcceptedEvent {
    
    private Long ticketId;
    private Long orderId;
    private LocalDateTime acceptedAt;
    
    public TicketAcceptedEvent() {
    }
    
    public TicketAcceptedEvent(Long ticketId, Long orderId, LocalDateTime acceptedAt) {
        this.ticketId = ticketId;
        this.orderId = orderId;
        this.acceptedAt = acceptedAt;
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
    
    public LocalDateTime getAcceptedAt() {
        return acceptedAt;
    }
    
    public void setAcceptedAt(LocalDateTime acceptedAt) {
        this.acceptedAt = acceptedAt;
    }
}
