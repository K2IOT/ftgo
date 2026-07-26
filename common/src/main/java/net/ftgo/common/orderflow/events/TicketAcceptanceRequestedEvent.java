package net.ftgo.common.orderflow.events;

import java.time.LocalDateTime;

public class TicketAcceptanceRequestedEvent {

    private String eventId;
    private Long ticketId;
    private Long orderId;
    private String acceptanceRequestId;
    private LocalDateTime occurredAt;

    public TicketAcceptanceRequestedEvent() {
    }

    public TicketAcceptanceRequestedEvent(
        String eventId,
        Long ticketId,
        Long orderId,
        String acceptanceRequestId,
        LocalDateTime occurredAt
    ) {
        this.eventId = eventId;
        this.ticketId = ticketId;
        this.orderId = orderId;
        this.acceptanceRequestId = acceptanceRequestId;
        this.occurredAt = occurredAt;
    }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public Long getTicketId() { return ticketId; }
    public void setTicketId(Long ticketId) { this.ticketId = ticketId; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public String getAcceptanceRequestId() { return acceptanceRequestId; }
    public void setAcceptanceRequestId(String acceptanceRequestId) {
        this.acceptanceRequestId = acceptanceRequestId;
    }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(LocalDateTime occurredAt) { this.occurredAt = occurredAt; }
}
