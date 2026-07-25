package net.ftgo.kitchen.api;

import net.ftgo.kitchen.domain.Ticket;
import net.ftgo.kitchen.domain.TicketLineItem;
import net.ftgo.kitchen.domain.TicketState;

import java.time.LocalDateTime;
import java.util.List;

/** DTO for Ticket responses. */
public class TicketDTO {

    private Long id;
    private Long restaurantId;
    private Long orderId;
    private TicketState state;
    private List<TicketLineItemDTO> lineItems;
    private LocalDateTime readyBy;
    private LocalDateTime acceptedAt;
    private LocalDateTime preparedAt;
    private String acceptanceRequestId;
    private LocalDateTime acceptanceRequestedAt;
    private String captureRequestId;
    private String acceptanceFailureReason;
    private LocalDateTime createdAt;

    public TicketDTO() {
    }

    public TicketDTO(Ticket ticket) {
        this.id = ticket.getId();
        this.restaurantId = ticket.getRestaurantId();
        this.orderId = ticket.getOrderId();
        this.state = ticket.getState();
        this.lineItems = ticket.getLineItems().stream()
            .map(TicketLineItemDTO::new)
            .toList();
        this.readyBy = ticket.getReadyBy();
        this.acceptedAt = ticket.getAcceptedAt();
        this.preparedAt = ticket.getPreparedAt();
        this.acceptanceRequestId = ticket.getAcceptanceRequestId();
        this.acceptanceRequestedAt = ticket.getAcceptanceRequestedAt();
        this.captureRequestId = ticket.getCaptureRequestId();
        this.acceptanceFailureReason = ticket.getAcceptanceFailureReason();
        this.createdAt = ticket.getCreatedAt();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getRestaurantId() { return restaurantId; }
    public void setRestaurantId(Long restaurantId) { this.restaurantId = restaurantId; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public TicketState getState() { return state; }
    public void setState(TicketState state) { this.state = state; }
    public List<TicketLineItemDTO> getLineItems() { return lineItems; }
    public void setLineItems(List<TicketLineItemDTO> lineItems) { this.lineItems = lineItems; }
    public LocalDateTime getReadyBy() { return readyBy; }
    public void setReadyBy(LocalDateTime readyBy) { this.readyBy = readyBy; }
    public LocalDateTime getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(LocalDateTime acceptedAt) { this.acceptedAt = acceptedAt; }
    public LocalDateTime getPreparedAt() { return preparedAt; }
    public void setPreparedAt(LocalDateTime preparedAt) { this.preparedAt = preparedAt; }
    public String getAcceptanceRequestId() { return acceptanceRequestId; }
    public void setAcceptanceRequestId(String acceptanceRequestId) {
        this.acceptanceRequestId = acceptanceRequestId;
    }
    public LocalDateTime getAcceptanceRequestedAt() { return acceptanceRequestedAt; }
    public void setAcceptanceRequestedAt(LocalDateTime acceptanceRequestedAt) {
        this.acceptanceRequestedAt = acceptanceRequestedAt;
    }
    public String getCaptureRequestId() { return captureRequestId; }
    public void setCaptureRequestId(String captureRequestId) {
        this.captureRequestId = captureRequestId;
    }
    public String getAcceptanceFailureReason() { return acceptanceFailureReason; }
    public void setAcceptanceFailureReason(String acceptanceFailureReason) {
        this.acceptanceFailureReason = acceptanceFailureReason;
    }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public static class TicketLineItemDTO {
        private Long id;
        private Long menuItemId;
        private String name;
        private Integer quantity;

        public TicketLineItemDTO() {
        }

        public TicketLineItemDTO(TicketLineItem lineItem) {
            this.id = lineItem.getId();
            this.menuItemId = lineItem.getMenuItemId();
            this.name = lineItem.getName();
            this.quantity = lineItem.getQuantity();
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getMenuItemId() { return menuItemId; }
        public void setMenuItemId(Long menuItemId) { this.menuItemId = menuItemId; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
    }
}
