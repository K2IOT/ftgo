package net.ftgo.kitchen.messaging;

import io.eventuate.tram.commands.common.Command;

/**
 * Command to confirm cancelling a ticket (from CancelOrderSaga).
 * Transitions ticket to CANCELLED state.
 */
public class ConfirmCancelTicketCommand implements Command {
    
    private Long ticketId;
    
    public ConfirmCancelTicketCommand() {
    }
    
    public ConfirmCancelTicketCommand(Long ticketId) {
        this.ticketId = ticketId;
    }
    
    public Long getTicketId() {
        return ticketId;
    }
    
    public void setTicketId(Long ticketId) {
        this.ticketId = ticketId;
    }
}
