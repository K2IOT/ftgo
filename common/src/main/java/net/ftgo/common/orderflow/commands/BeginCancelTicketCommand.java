package net.ftgo.common.orderflow.commands;

import io.eventuate.tram.commands.common.Command;

public class BeginCancelTicketCommand implements Command {

    private Long ticketId;

    public BeginCancelTicketCommand() {
    }

    public BeginCancelTicketCommand(Long ticketId) {
        this.ticketId = ticketId;
    }

    public Long getTicketId() {
        return ticketId;
    }

    public void setTicketId(Long ticketId) {
        this.ticketId = ticketId;
    }
}
