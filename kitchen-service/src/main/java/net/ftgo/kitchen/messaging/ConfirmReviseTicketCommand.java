package net.ftgo.kitchen.messaging;

import io.eventuate.tram.commands.common.Command;

import java.util.List;

/**
 * Command to confirm revising a ticket (from ReviseOrderSaga).
 * Updates line items to the revised version.
 */
public class ConfirmReviseTicketCommand implements Command {
    
    private Long ticketId;
    private List<CreateTicketCommand.TicketLineItemDTO> revisedLineItems;
    
    public ConfirmReviseTicketCommand() {
    }
    
    public ConfirmReviseTicketCommand(Long ticketId, List<CreateTicketCommand.TicketLineItemDTO> revisedLineItems) {
        this.ticketId = ticketId;
        this.revisedLineItems = revisedLineItems;
    }
    
    public Long getTicketId() {
        return ticketId;
    }
    
    public void setTicketId(Long ticketId) {
        this.ticketId = ticketId;
    }
    
    public List<CreateTicketCommand.TicketLineItemDTO> getRevisedLineItems() {
        return revisedLineItems;
    }
    
    public void setRevisedLineItems(List<CreateTicketCommand.TicketLineItemDTO> revisedLineItems) {
        this.revisedLineItems = revisedLineItems;
    }
}
