package net.ftgo.common.orderflow.commands;

import io.eventuate.tram.commands.common.Command;

public class ConfirmTicketAcceptanceCommand implements Command {

    private Long ticketId;
    private String captureRequestId;

    public ConfirmTicketAcceptanceCommand() {
    }

    public ConfirmTicketAcceptanceCommand(Long ticketId, String captureRequestId) {
        this.ticketId = ticketId;
        this.captureRequestId = captureRequestId;
    }

    public Long getTicketId() { return ticketId; }
    public void setTicketId(Long ticketId) { this.ticketId = ticketId; }
    public String getCaptureRequestId() { return captureRequestId; }
    public void setCaptureRequestId(String captureRequestId) {
        this.captureRequestId = captureRequestId;
    }
}
