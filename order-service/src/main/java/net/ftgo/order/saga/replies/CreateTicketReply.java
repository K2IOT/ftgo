package net.ftgo.order.saga.replies;

/**
 * Reply from Kitchen Service after creating a ticket.
 * 
 * Contains the ID of the created ticket, which is stored in the saga data
 * for use in subsequent saga steps (approve, cancel).
 */
public class CreateTicketReply {
    
    private Long ticketId;
    
    /**
     * Default constructor for serialization.
     */
    public CreateTicketReply() {
    }
    
    /**
     * Creates a create ticket reply.
     * 
     * @param ticketId the ID of the created ticket
     */
    public CreateTicketReply(Long ticketId) {
        this.ticketId = ticketId;
    }
    
    public Long getTicketId() {
        return ticketId;
    }
    
    public void setTicketId(Long ticketId) {
        this.ticketId = ticketId;
    }
}
