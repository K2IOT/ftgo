package net.ftgo.kitchen.messaging;

import io.eventuate.tram.commands.common.Command;

/**
 * Command to undo revising a ticket (compensation for ReviseOrderSaga).
 * Restores ticket to its previous state using the stored previousState field.
 */
public class UndoReviseTicketCommand implements Command {
    
    private Long ticketId;
    
    public UndoReviseTicketCommand() {
    }
    
    public UndoReviseTicketCommand(Long ticketId) {
        this.ticketId = ticketId;
    }
    
    public Long getTicketId() {
        return ticketId;
    }
    
    public void setTicketId(Long ticketId) {
        this.ticketId = ticketId;
    }
}
