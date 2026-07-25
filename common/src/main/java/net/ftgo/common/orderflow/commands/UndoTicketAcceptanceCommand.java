package net.ftgo.common.orderflow.commands;

import io.eventuate.tram.commands.common.Command;

public class UndoTicketAcceptanceCommand implements Command {

    private Long ticketId;
    private String reason;

    public UndoTicketAcceptanceCommand() {
    }

    public UndoTicketAcceptanceCommand(Long ticketId, String reason) {
        this.ticketId = ticketId;
        this.reason = reason;
    }

    public Long getTicketId() { return ticketId; }
    public void setTicketId(Long ticketId) { this.ticketId = ticketId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
