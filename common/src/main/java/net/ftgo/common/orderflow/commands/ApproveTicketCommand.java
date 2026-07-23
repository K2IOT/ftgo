package net.ftgo.common.orderflow.commands;

import io.eventuate.tram.commands.common.Command;

import java.time.LocalDateTime;

public class ApproveTicketCommand implements Command {

    private Long ticketId;
    private LocalDateTime acceptanceDeadline;

    public ApproveTicketCommand() {
    }

    public ApproveTicketCommand(Long ticketId) {
        this(ticketId, null);
    }

    public ApproveTicketCommand(Long ticketId, LocalDateTime acceptanceDeadline) {
        this.ticketId = ticketId;
        this.acceptanceDeadline = acceptanceDeadline;
    }

    public Long getTicketId() {
        return ticketId;
    }

    public void setTicketId(Long ticketId) {
        this.ticketId = ticketId;
    }

    public LocalDateTime getAcceptanceDeadline() {
        return acceptanceDeadline;
    }

    public void setAcceptanceDeadline(LocalDateTime acceptanceDeadline) {
        this.acceptanceDeadline = acceptanceDeadline;
    }
}
