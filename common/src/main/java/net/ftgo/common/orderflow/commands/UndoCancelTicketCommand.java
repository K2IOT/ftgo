package net.ftgo.common.orderflow.commands;

import io.eventuate.tram.commands.common.Command;

public class UndoCancelTicketCommand implements Command {

    private Long ticketId;

    public UndoCancelTicketCommand() {
    }

    public UndoCancelTicketCommand(Long ticketId) {
        this.ticketId = ticketId;
    }

    public Long getTicketId() {
        return ticketId;
    }

    public void setTicketId(Long ticketId) {
        this.ticketId = ticketId;
    }
}
