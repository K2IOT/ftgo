package net.ftgo.kitchen.messaging;

import io.eventuate.tram.commands.common.Command;

/**
 * Command to approve a ticket (from CreateOrderSaga).
 * Transitions ticket from CREATE_PENDING to AWAITING_ACCEPTANCE.
 */
public class ApproveTicketCommand implements Command {
    
    private Long ticketId;
    
    public ApproveTicketCommand() {
    }
    
    public ApproveTicketCommand(Long ticketId) {
        this.ticketId = ticketId;
    }
    
    public Long getTicketId() {
        return ticketId;
    }
    
    public void setTicketId(Long ticketId) {
        this.ticketId = ticketId;
    }
}
