package net.ftgo.common.orderflow.commands;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.eventuate.tram.commands.common.Command;

import java.time.LocalDateTime;

public class ApproveTicketCommand implements Command {

    private Long ticketId;

    /**
     * Eventuate command JSON uses a static ObjectMapper without JSR-310.
     * Keep the wire value as ISO text and expose LocalDateTime to Java callers.
     */
    @JsonProperty("acceptanceDeadline")
    private String acceptanceDeadlineIso;

    public ApproveTicketCommand() {
    }

    public ApproveTicketCommand(Long ticketId) {
        this.ticketId = ticketId;
    }

    public ApproveTicketCommand(Long ticketId, LocalDateTime acceptanceDeadline) {
        this.ticketId = ticketId;
        setAcceptanceDeadline(acceptanceDeadline);
    }

    public Long getTicketId() {
        return ticketId;
    }

    public void setTicketId(Long ticketId) {
        this.ticketId = ticketId;
    }

    @JsonIgnore
    public LocalDateTime getAcceptanceDeadline() {
        return acceptanceDeadlineIso == null ? null : LocalDateTime.parse(acceptanceDeadlineIso);
    }

    @JsonIgnore
    public void setAcceptanceDeadline(LocalDateTime acceptanceDeadline) {
        this.acceptanceDeadlineIso = acceptanceDeadline == null ? null : acceptanceDeadline.toString();
    }
}
