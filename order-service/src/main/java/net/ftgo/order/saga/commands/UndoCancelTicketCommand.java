package net.ftgo.order.saga.commands;

import io.eventuate.tram.commands.common.Command;

/**
 * Command to undo ticket cancellation in Kitchen Service.
 * 
 * This is a compensation command for beginCancelTicket. It restores the ticket
 * to its previous state when CancelOrderSaga fails before the pivot point.
 */
public class UndoCancelTicketCommand implements Command {
    
    private Long ticketId;
    
    /**
     * Default constructor for serialization.
     */
    public UndoCancelTicketCommand() {
    }
    
    /**
     * Creates a command to undo ticket cancellation.
     * 
     * @param ticketId the ticket ID to restore
     */
    public UndoCancelTicketCommand(Long ticketId) {
        this.ticketId = ticketId;
    }
    
    public Long getTicketId() {
        return ticketId;
    }
    
    public void setTicketId(Long ticketId) {
        this.ticketId = ticketId;
    }
    
    @Override
    public String toString() {
        return String.format("UndoCancelTicketCommand{ticketId=%d}", ticketId);
    }
}
