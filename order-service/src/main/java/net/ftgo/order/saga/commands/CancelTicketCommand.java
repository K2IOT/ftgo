package net.ftgo.order.saga.commands;

import io.eventuate.tram.commands.common.Command;

/**
 * Command sent to Kitchen Service to cancel a ticket.
 * 
 * Used as compensation in CreateOrderSaga when the saga fails before the pivot point.
 * Kitchen Service transitions the ticket to CANCELLED state.
 */
public class CancelTicketCommand implements Command {
    
    private Long ticketId;
    
    /**
     * Default constructor for serialization.
     */
    public CancelTicketCommand() {
    }
    
    /**
     * Creates a cancel ticket command.
     * 
     * @param ticketId the ticket ID to cancel
     */
    public CancelTicketCommand(Long ticketId) {
        this.ticketId = ticketId;
    }
    
    public Long getTicketId() {
        return ticketId;
    }
    
    public void setTicketId(Long ticketId) {
        this.ticketId = ticketId;
    }
}
