package net.ftgo.order.saga.commands;

import io.eventuate.tram.commands.common.Command;

/**
 * Command to begin ticket cancellation in Kitchen Service.
 * 
 * This is a compensatable step in CancelOrderSaga. If the saga fails before
 * the pivot point (reverseAuthorization), this command will be compensated
 * by undoCancelTicket.
 */
public class BeginCancelTicketCommand implements Command {
    
    private Long ticketId;
    
    /**
     * Default constructor for serialization.
     */
    public BeginCancelTicketCommand() {
    }
    
    /**
     * Creates a command to begin ticket cancellation.
     * 
     * @param ticketId the ticket ID to cancel
     */
    public BeginCancelTicketCommand(Long ticketId) {
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
        return String.format("BeginCancelTicketCommand{ticketId=%d}", ticketId);
    }
}
