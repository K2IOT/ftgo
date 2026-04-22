package net.ftgo.order.saga.commands;

import io.eventuate.tram.commands.common.Command;

/**
 * Command to confirm ticket cancellation in Kitchen Service.
 * 
 * This is a retriable step in CancelOrderSaga that occurs after the pivot point.
 * It finalizes the ticket cancellation after payment authorization has been reversed.
 */
public class ConfirmCancelTicketCommand implements Command {
    
    private Long ticketId;
    
    /**
     * Default constructor for serialization.
     */
    public ConfirmCancelTicketCommand() {
    }
    
    /**
     * Creates a command to confirm ticket cancellation.
     * 
     * @param ticketId the ticket ID to confirm cancellation
     */
    public ConfirmCancelTicketCommand(Long ticketId) {
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
        return String.format("ConfirmCancelTicketCommand{ticketId=%d}", ticketId);
    }
}
