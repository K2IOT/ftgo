package net.ftgo.common.orderflow.events;

import java.time.LocalDateTime;

public class TicketAcceptedEvent {

    private String eventId;
    private Long ticketId;
    private Long orderId;
    private LocalDateTime occurredAt;

    public TicketAcceptedEvent() {
    }

    public TicketAcceptedEvent(String eventId, Long ticketId, Long orderId, LocalDateTime occurredAt) {
        this.eventId = eventId;
        this.ticketId = ticketId;
        this.orderId = orderId;
        this.occurredAt = occurredAt;
    }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public Long getTicketId() { return ticketId; }
    public void setTicketId(Long ticketId) { this.ticketId = ticketId; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(LocalDateTime occurredAt) { this.occurredAt = occurredAt; }
}
