package net.ftgo.order.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import net.ftgo.common.Money;
import net.ftgo.order.domain.Order;
import net.ftgo.order.domain.OrderLineItem;
import net.ftgo.order.domain.OrderState;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Response DTO for order details.
 * 
 * Contains complete order information including state, line items, and totals.
 */
public class OrderResponse {
    
    @JsonProperty
    private final Long id;
    
    @JsonProperty
    private final OrderState state;
    
    @JsonProperty
    private final Long consumerId;
    
    @JsonProperty
    private final Long restaurantId;
    
    @JsonProperty
    private final List<OrderLineItemResponse> lineItems;
    
    @JsonProperty
    private final String deliveryAddress;
    
    @JsonProperty
    private final LocalDateTime deliveryTime;
    
    @JsonProperty
    private final Money orderTotal;
    
    @JsonProperty
    private final LocalDateTime createdAt;
    
    @JsonProperty
    private final LocalDateTime updatedAt;
    
    public OrderResponse(Long id, OrderState state, Long consumerId, Long restaurantId,
                        List<OrderLineItemResponse> lineItems, String deliveryAddress,
                        LocalDateTime deliveryTime, Money orderTotal,
                        LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.state = state;
        this.consumerId = consumerId;
        this.restaurantId = restaurantId;
        this.lineItems = lineItems;
        this.deliveryAddress = deliveryAddress;
        this.deliveryTime = deliveryTime;
        this.orderTotal = orderTotal;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
    
    /**
     * Creates an OrderResponse from an Order entity.
     * 
     * @param order the order entity
     * @return the order response DTO
     */
    public static OrderResponse fromOrder(Order order) {
        List<OrderLineItemResponse> lineItemResponses = order.getLineItems().stream()
            .map(OrderLineItemResponse::fromOrderLineItem)
            .collect(Collectors.toList());
        
        return new OrderResponse(
            order.getId(),
            order.getState(),
            order.getConsumerId(),
            order.getRestaurantId(),
            lineItemResponses,
            order.getDeliveryInfo().getDeliveryAddress(),
            order.getDeliveryInfo().getDeliveryTime(),
            order.getOrderTotal(),
            order.getCreatedAt(),
            order.getUpdatedAt()
        );
    }
    
    // Getters
    
    public Long getId() {
        return id;
    }
    
    public OrderState getState() {
        return state;
    }
    
    public Long getConsumerId() {
        return consumerId;
    }
    
    public Long getRestaurantId() {
        return restaurantId;
    }
    
    public List<OrderLineItemResponse> getLineItems() {
        return lineItems;
    }
    
    public String getDeliveryAddress() {
        return deliveryAddress;
    }
    
    public LocalDateTime getDeliveryTime() {
        return deliveryTime;
    }
    
    public Money getOrderTotal() {
        return orderTotal;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
