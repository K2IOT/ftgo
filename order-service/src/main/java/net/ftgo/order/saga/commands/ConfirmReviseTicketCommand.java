package net.ftgo.order.saga.commands;

import io.eventuate.tram.commands.common.Command;

/**
 * Command to confirm ticket revision in Kitchen Service.
 * 
 * This is a retriable step in ReviseOrderSaga that occurs after the pivot point.
 * It finalizes the ticket revision after payment authorization has been adjusted.
 */
public class ConfirmReviseTicketCommand implements Command {
    
    private Long ticketId;
    
    /**
     * Default constructor for serialization.
     */
    public ConfirmReviseTicketCommand() {
    }
    
    /**
     * Creates a command to confirm ticket revision.
     * 
     * @param ticketId the ticket ID to confirm revision
     */
    public ConfirmReviseTicketCommand(Long ticketId) {
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
        return String.format("ConfirmReviseTicketCommand{ticketId=%d}", ticketId);
    }
}
