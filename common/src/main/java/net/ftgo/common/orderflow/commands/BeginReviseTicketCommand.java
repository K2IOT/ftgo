package net.ftgo.common.orderflow.commands;

import io.eventuate.tram.commands.common.Command;

import java.util.List;

public class BeginReviseTicketCommand implements Command {

    private Long ticketId;
    private List<CreateTicketCommand.TicketLineItemDTO> revisedLineItems;

    public BeginReviseTicketCommand() {
    }

    public BeginReviseTicketCommand(Long ticketId, List<CreateTicketCommand.TicketLineItemDTO> revisedLineItems) {
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
