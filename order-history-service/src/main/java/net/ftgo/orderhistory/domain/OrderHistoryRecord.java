package net.ftgo.orderhistory.domain;

import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Order history record stored in ScyllaDB (CQRS read model).
 * 
 * This denormalized view aggregates data from multiple services:
 * - Order Service: order details, status, line items
 * - Kitchen Service: ticket status
 * - Delivery Service: delivery status
 * - Accounting Service: authorization status
 * 
 * The record is updated by consuming domain events from Kafka topics.
 * Idempotent event processing ensures eventual consistency.
 */
@Table("order_history")
public class OrderHistoryRecord {
    
    @PrimaryKey
    private String orderId;
    
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
    
    /**
     * Default constructor for Cassandra mapping.
     */
    public OrderHistoryRecord() {
    }
    
    /**
     * Creates a new OrderHistoryRecord.
     * 
     * @param orderId the order ID
     */
    public OrderHistoryRecord(String orderId) {
        this.orderId = orderId;
        this.updatedAt = LocalDateTime.now();
    }
    
    public String getOrderId() {
        return orderId;
    }
    
    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }
    
    public Long getConsumerId() {
        return consumerId;
    }
    
    public void setConsumerId(Long consumerId) {
        this.consumerId = consumerId;
    }
    
    public Long getRestaurantId() {
        return restaurantId;
    }
    
    public void setRestaurantId(Long restaurantId) {
        this.restaurantId = restaurantId;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }
    
    public BigDecimal getOrderTotal() {
        return orderTotal;
    }
    
    public void setOrderTotal(BigDecimal orderTotal) {
        this.orderTotal = orderTotal;
    }
    
    public List<LineItem> getLineItems() {
        return lineItems;
    }
    
    public void setLineItems(List<LineItem> lineItems) {
        this.lineItems = lineItems;
    }
    
    public String getDeliveryAddress() {
        return deliveryAddress;
    }
    
    public void setDeliveryAddress(String deliveryAddress) {
        this.deliveryAddress = deliveryAddress;
    }
    
    public LocalDateTime getDeliveryTime() {
        return deliveryTime;
    }
    
    public void setDeliveryTime(LocalDateTime deliveryTime) {
        this.deliveryTime = deliveryTime;
    }
    
    public String getDeliveryStatus() {
        return deliveryStatus;
    }
    
    public void setDeliveryStatus(String deliveryStatus) {
        this.deliveryStatus = deliveryStatus;
        this.updatedAt = LocalDateTime.now();
    }
    
    public String getTicketStatus() {
        return ticketStatus;
    }
    
    public void setTicketStatus(String ticketStatus) {
        this.ticketStatus = ticketStatus;
        this.updatedAt = LocalDateTime.now();
    }
    
    public String getAuthorizationStatus() {
        return authorizationStatus;
    }
    
    public void setAuthorizationStatus(String authorizationStatus) {
        this.authorizationStatus = authorizationStatus;
        this.updatedAt = LocalDateTime.now();
    }
    
    public LocalDateTime getCreationDate() {
        return creationDate;
    }
    
    public void setCreationDate(LocalDateTime creationDate) {
        this.creationDate = creationDate;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    public Set<String> getKeywords() {
        return keywords;
    }
    
    public void setKeywords(Set<String> keywords) {
        this.keywords = keywords;
    }
    
    @Override
    public String toString() {
        return "OrderHistoryRecord{" +
               "orderId='" + orderId + '\'' +
               ", consumerId=" + consumerId +
               ", restaurantId=" + restaurantId +
               ", status='" + status + '\'' +
               ", orderTotal=" + orderTotal +
               ", deliveryStatus='" + deliveryStatus + '\'' +
               ", ticketStatus='" + ticketStatus + '\'' +
               ", authorizationStatus='" + authorizationStatus + '\'' +
               ", creationDate=" + creationDate +
               ", updatedAt=" + updatedAt +
               '}';
    }
}
