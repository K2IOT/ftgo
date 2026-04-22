package net.ftgo.order.saga.commands;

import io.eventuate.tram.commands.common.Command;
import net.ftgo.order.domain.OrderLineItem;

import java.util.List;

/**
 * Command to begin ticket revision in Kitchen Service.
 * 
 * This is a compensatable step in ReviseOrderSaga. If the saga fails before
 * the pivot point (reviseCreditCardAuthorization), this command will be compensated
 * by undoReviseTicket.
 */
public class BeginReviseTicketCommand implements Command {
    
    private Long ticketId;
    private List<OrderLineItem> revisedLineItems;
    
    /**
     * Default constructor for serialization.
     */
    public BeginReviseTicketCommand() {
    }
    
    /**
     * Creates a command to begin ticket revision.
     * 
     * @param ticketId the ticket ID to revise
     * @param revisedLineItems the new line items
     */
    public BeginReviseTicketCommand(Long ticketId, List<OrderLineItem> revisedLineItems) {
        this.ticketId = ticketId;
        this.revisedLineItems = revisedLineItems;
    }
    
    public Long getTicketId() {
        return ticketId;
    }
    
    public void setTicketId(Long ticketId) {
        this.ticketId = ticketId;
    }
    
    public List<OrderLineItem> getRevisedLineItems() {
        return revisedLineItems;
    }
    
    public void setRevisedLineItems(List<OrderLineItem> revisedLineItems) {
        this.revisedLineItems = revisedLineItems;
    }
    
    @Override
    public String toString() {
        return String.format("BeginReviseTicketCommand{ticketId=%d, itemCount=%d}", 
            ticketId, revisedLineItems != null ? revisedLineItems.size() : 0);
    }
}
