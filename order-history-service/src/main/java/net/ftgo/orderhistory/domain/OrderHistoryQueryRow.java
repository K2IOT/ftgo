package net.ftgo.orderhistory.domain;

import org.springframework.data.cassandra.core.mapping.Column;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/** Shared non-key columns for dedicated Scylla query tables. */
public abstract class OrderHistoryQueryRow {

    @Column("order_total")
    private BigDecimal orderTotal;

    @Column("line_items")
    private List<LineItem> lineItems;

    @Column("delivery_address")
    private String deliveryAddress;

    @Column("delivery_time")
    private LocalDateTime deliveryTime;

    @Column("delivery_status")
    private String deliveryStatus;

    @Column("ticket_status")
    private String ticketStatus;

    @Column("authorization_status")
    private String authorizationStatus;

    @Column("updated_at")
    private LocalDateTime updatedAt;

    @Column("keywords")
    private Set<String> keywords;

    protected OrderHistoryQueryRow() {
    }

    protected void copyFrom(OrderHistoryRecord record) {
        orderTotal = record.getOrderTotal();
        lineItems = record.getLineItems();
        deliveryAddress = record.getDeliveryAddress();
        deliveryTime = record.getDeliveryTime();
        deliveryStatus = record.getDeliveryStatus();
        ticketStatus = record.getTicketStatus();
        authorizationStatus = record.getAuthorizationStatus();
        updatedAt = record.getUpdatedAt();
        keywords = record.getKeywords();
    }

    protected abstract Long consumerId();

    protected abstract Long restaurantId();

    protected abstract String status();

    protected abstract LocalDateTime creationDate();

    public abstract String orderId();

    public OrderHistoryRecord toRecord() {
        OrderHistoryRecord record = new OrderHistoryRecord(orderId());
        record.setConsumerId(consumerId());
        record.setRestaurantId(restaurantId());
        record.setStatus(status());
        record.setOrderTotal(orderTotal);
        record.setLineItems(lineItems);
        record.setDeliveryAddress(deliveryAddress);
        record.setDeliveryTime(deliveryTime);
        record.setDeliveryStatus(deliveryStatus);
        record.setTicketStatus(ticketStatus);
        record.setAuthorizationStatus(authorizationStatus);
        record.setCreationDate(creationDate());
        record.setUpdatedAt(updatedAt);
        record.setKeywords(keywords);
        return record;
    }

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
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Set<String> getKeywords() { return keywords; }
    public void setKeywords(Set<String> keywords) { this.keywords = keywords; }
}
