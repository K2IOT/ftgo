package net.ftgo.kitchen.messaging;

import io.eventuate.tram.commands.common.Command;

/**
 * Command to undo cancelling a ticket (compensation for CancelOrderSaga).
 */
public class UndoCancelTicketCommand implements Command {
    
    private Long ticketId;
    
    public UndoCancelTicketCommand() {
    }
    
    public UndoCancelTicketCommand(Long ticketId) {
        this.ticketId = ticketId;
    }
    
    public Long getTicketId() {
        return ticketId;
    }
    
    public void setTicketId(Long ticketId) {
        this.ticketId = ticketId;
    }
}
