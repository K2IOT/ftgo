package net.ftgo.orderhistory.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/** Shared columns for dedicated Scylla query tables. */
public abstract class OrderHistoryQueryRow {

    private Long consumerId;
    private Long restaurantId;
    private String status;
    private BigDecimal orderTotal;
    private List<LineItem> lineItems;
    private String deliveryAddress;
    private LocalDateTime deliveryTime;
    private String deliveryStatus;
    private String ticketStatus;
    private String authorizationStatus;
    private LocalDateTime creationDate;
    private LocalDateTime updatedAt;
    private Set<String> keywords;

    protected OrderHistoryQueryRow() {
    }

    protected void copyFrom(OrderHistoryRecord record) {
        consumerId = record.getConsumerId();
        restaurantId = record.getRestaurantId();
        status = record.getStatus();
        orderTotal = record.getOrderTotal();
        lineItems = record.getLineItems();
        deliveryAddress = record.getDeliveryAddress();
        deliveryTime = record.getDeliveryTime();
        deliveryStatus = record.getDeliveryStatus();
        ticketStatus = record.getTicketStatus();
        authorizationStatus = record.getAuthorizationStatus();
        creationDate = record.getCreationDate();
        updatedAt = record.getUpdatedAt();
        keywords = record.getKeywords();
    }

    public abstract String orderId();

    public OrderHistoryRecord toRecord() {
        OrderHistoryRecord record = new OrderHistoryRecord(orderId());
        record.setConsumerId(consumerId);
        record.setRestaurantId(restaurantId);
        record.setStatus(status);
        record.setOrderTotal(orderTotal);
        record.setLineItems(lineItems);
        record.setDeliveryAddress(deliveryAddress);
        record.setDeliveryTime(deliveryTime);
        record.setDeliveryStatus(deliveryStatus);
        record.setTicketStatus(ticketStatus);
        record.setAuthorizationStatus(authorizationStatus);
        record.setCreationDate(creationDate);
        record.setUpdatedAt(updatedAt);
        record.setKeywords(keywords);
        return record;
    }

    public Long getConsumerId() { return consumerId; }
    public void setConsumerId(Long consumerId) { this.consumerId = consumerId; }
    public Long getRestaurantId() { return restaurantId; }
    public void setRestaurantId(Long restaurantId) { this.restaurantId = restaurantId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public BigDecimal getOrderTotal() { return orderTotal; }
    public void setOrderTotal(BigDecimal orderTotal) { this.orderTotal = orderTotal; }
    public List<LineItem> getLineItems() { return lineItems; }
    public void setLineItems(List<LineItem> lineItems) { this.lineItems = lineItems; }
    public String getDeliveryAddress() { return deliveryAddress; }
    public void setDeliveryAddress(String deliveryAddress) { this.deliveryAddress = deliveryAddress; }
    public LocalDateTime getDeliveryTime() { return deliveryTime; }
    public void setDeliveryTime(LocalDateTime deliveryTime) { this.deliveryTime = deliveryTime; }
    public String getDeliveryStatus() { return deliveryStatus; }
    public void setDeliveryStatus(String deliveryStatus) { this.deliveryStatus = deliveryStatus; }
    public String getTicketStatus() { return ticketStatus; }
    public void setTicketStatus(String ticketStatus) { this.ticketStatus = ticketStatus; }
    public String getAuthorizationStatus() { return authorizationStatus; }
    public void setAuthorizationStatus(String authorizationStatus) { this.authorizationStatus = authorizationStatus; }
    public LocalDateTime getCreationDate() { return creationDate; }
    public void setCreationDate(LocalDateTime creationDate) { this.creationDate = creationDate; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Set<String> getKeywords() { return keywords; }
    public void setKeywords(Set<String> keywords) { this.keywords = keywords; }
}
