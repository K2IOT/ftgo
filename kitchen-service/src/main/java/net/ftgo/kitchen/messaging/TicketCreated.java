package net.ftgo.kitchen.messaging;

/**
 * Reply message indicating ticket was created successfully.
 */
public class TicketCreated {
    
    private Long ticketId;
    
    public TicketCreated() {
    }
    
    public TicketCreated(Long ticketId) {
        this.ticketId = ticketId;
    }
    
    public Long getTicketId() {
        return ticketId;
    }
    
    public void setTicketId(Long ticketId) {
        this.ticketId = ticketId;
    }
}
