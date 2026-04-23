package net.ftgo.orderhistory.messaging;

import java.math.BigDecimal;
import java.util.List;

/**
 * Domain event published when an order is revised.
 * 
 * Published by Order Service to net.ftgo.orderservice.domain.Order topic.
 */
public class OrderRevisedEvent {
    
    private Long orderId;
    private BigDecimal orderTotal;
    private List<OrderCreatedEvent.OrderLineItemDto> lineItems;
    
    /**
     * Default constructor for JSON deserialization.
     */
    public OrderRevisedEvent() {
    }
    
    public Long getOrderId() {
        return orderId;
    }
    
    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }
    
    public BigDecimal getOrderTotal() {
        return orderTotal;
    }
    
    public void setOrderTotal(BigDecimal orderTotal) {
        this.orderTotal = orderTotal;
    }
    
    public List<OrderCreatedEvent.OrderLineItemDto> getLineItems() {
        return lineItems;
    }
    
    public void setLineItems(List<OrderCreatedEvent.OrderLineItemDto> lineItems) {
        this.lineItems = lineItems;
    }
}
