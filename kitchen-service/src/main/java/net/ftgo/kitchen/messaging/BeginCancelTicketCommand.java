package net.ftgo.kitchen.messaging;

import io.eventuate.tram.commands.common.Command;

/**
 * Command to begin cancelling a ticket (from CancelOrderSaga).
 */
public class BeginCancelTicketCommand implements Command {
    
    private Long ticketId;
    
    public BeginCancelTicketCommand() {
    }
    
    public BeginCancelTicketCommand(Long ticketId) {
        this.ticketId = ticketId;
    }
    
    public Long getTicketId() {
        return ticketId;
    }
    
    public void setTicketId(Long ticketId) {
        this.ticketId = ticketId;
    }
}
