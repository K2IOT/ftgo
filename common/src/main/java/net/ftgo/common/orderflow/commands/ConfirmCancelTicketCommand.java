package net.ftgo.common.orderflow.commands;

import io.eventuate.tram.commands.common.Command;

public class ConfirmCancelTicketCommand implements Command {

    private Long ticketId;

    public ConfirmCancelTicketCommand() {
    }

    public ConfirmCancelTicketCommand(Long ticketId) {
        this.ticketId = ticketId;
    }

    public Long getTicketId() {
        return ticketId;
    }

    public void setTicketId(Long ticketId) {
        this.ticketId = ticketId;
    }
}
