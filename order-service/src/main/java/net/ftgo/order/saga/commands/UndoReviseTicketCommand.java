package net.ftgo.order.saga.commands;

import io.eventuate.tram.commands.common.Command;

/**
 * Command to undo ticket revision in Kitchen Service.
 * 
 * This is a compensation command for beginReviseTicket. It restores the ticket
 * to its previous state when ReviseOrderSaga fails before the pivot point.
 */
public class UndoReviseTicketCommand implements Command {
    
    private Long ticketId;
    
    /**
     * Default constructor for serialization.
     */
    public UndoReviseTicketCommand() {
    }
    
    /**
     * Creates a command to undo ticket revision.
     * 
     * @param ticketId the ticket ID to restore
     */
    public UndoReviseTicketCommand(Long ticketId) {
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
        return String.format("UndoReviseTicketCommand{ticketId=%d}", ticketId);
    }
}
