package net.ftgo.order.saga.commands;

import io.eventuate.tram.commands.common.Command;

/**
 * Command sent to Kitchen Service to approve a ticket.
 * 
 * Part of CreateOrderSaga step 5 (retriable step after pivot).
 * Kitchen Service transitions the ticket from CREATE_PENDING to AWAITING_ACCEPTANCE state.
 * 
 * This step is retriable because it occurs after the payment authorization pivot point.
 * If it fails, the saga will retry until success rather than compensating.
 */
public class ApproveTicketCommand implements Command {
    
    private Long ticketId;
    
    /**
     * Default constructor for serialization.
     */
    public ApproveTicketCommand() {
    }
    
    /**
     * Creates an approve ticket command.
     * 
     * @param ticketId the ticket ID to approve
     */
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
