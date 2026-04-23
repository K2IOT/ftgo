package net.ftgo.kitchen.messaging;

import io.eventuate.tram.commands.common.Command;

import java.util.List;

/**
 * Command to undo revising a ticket (compensation for ReviseOrderSaga).
 * Restores original line items.
 */
public class UndoReviseTicketCommand implements Command {
    
    private Long ticketId;
    private List<CreateTicketCommand.TicketLineItemDTO> originalLineItems;
    
    public UndoReviseTicketCommand() {
    }
    
    public UndoReviseTicketCommand(Long ticketId, List<CreateTicketCommand.TicketLineItemDTO> originalLineItems) {
        this.ticketId = ticketId;
        this.originalLineItems = originalLineItems;
    }
    
    public Long getTicketId() {
        return ticketId;
    }
    
    public void setTicketId(Long ticketId) {
        this.ticketId = ticketId;
    }
    
    public List<CreateTicketCommand.TicketLineItemDTO> getOriginalLineItems() {
        return originalLineItems;
    }
    
    public void setOriginalLineItems(List<CreateTicketCommand.TicketLineItemDTO> originalLineItems) {
        this.originalLineItems = originalLineItems;
    }
}
