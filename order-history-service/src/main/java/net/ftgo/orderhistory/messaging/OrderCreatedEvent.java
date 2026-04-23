package net.ftgo.orderhistory.messaging;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Domain event published when an order is created.
 * 
 * Published by Order Service to net.ftgo.orderservice.domain.Order topic.
 */
public class OrderCreatedEvent {
    
    private Long orderId;
    private Long consumerId;
    private Long restaurantId;
    private String status;
    private BigDecimal orderTotal;
    private List<OrderLineItemDto> lineItems;
    private String deliveryAddress;
    private LocalDateTime deliveryTime;
    private LocalDateTime createdAt;
    
    /**
     * Default constructor for JSON deserialization.
     */
    public OrderCreatedEvent() {
    }
    
    public Long getOrderId() {
        return orderId;
    }
    
    public void setOrderId(Long orderId) {
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
    }
    
    public BigDecimal getOrderTotal() {
        return orderTotal;
    }
    
    public void setOrderTotal(BigDecimal orderTotal) {
        this.orderTotal = orderTotal;
    }
    
    public List<OrderLineItemDto> getLineItems() {
        return lineItems;
    }
    
    public void setLineItems(List<OrderLineItemDto> lineItems) {
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
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    /**
     * DTO for order line items in events.
     */
    public static class OrderLineItemDto {
        private Long menuItemId;
        private String name;
        private BigDecimal price;
        private Integer quantity;
        
        public OrderLineItemDto() {
        }
        
        public Long getMenuItemId() {
            return menuItemId;
        }
        
        public void setMenuItemId(Long menuItemId) {
            this.menuItemId = menuItemId;
        }
        
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        public BigDecimal getPrice() {
            return price;
        }
        
        public void setPrice(BigDecimal price) {
            this.price = price;
        }
        
        public Integer getQuantity() {
            return quantity;
        }
        
        public void setQuantity(Integer quantity) {
            this.quantity = quantity;
        }
    }
}
