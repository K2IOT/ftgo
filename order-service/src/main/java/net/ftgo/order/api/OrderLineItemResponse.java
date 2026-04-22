package net.ftgo.order.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import net.ftgo.common.Money;
import net.ftgo.order.domain.OrderLineItem;

/**
 * Response DTO for an order line item.
 */
public class OrderLineItemResponse {
    
    @JsonProperty
    private final Long menuItemId;
    
    @JsonProperty
    private final String name;
    
    @JsonProperty
    private final Money price;
    
    @JsonProperty
    private final int quantity;
    
    @JsonProperty
    private final Money total;
    
    public OrderLineItemResponse(Long menuItemId, String name, Money price, int quantity, Money total) {
        this.menuItemId = menuItemId;
        this.name = name;
        this.price = price;
        this.quantity = quantity;
        this.total = total;
    }
    
    /**
     * Creates an OrderLineItemResponse from an OrderLineItem entity.
     * 
     * @param item the order line item entity
     * @return the order line item response DTO
     */
    public static OrderLineItemResponse fromOrderLineItem(OrderLineItem item) {
        return new OrderLineItemResponse(
            item.getMenuItemId(),
            item.getName(),
            item.getPrice(),
            item.getQuantity(),
            item.getTotal()
        );
    }
    
    // Getters
    
    public Long getMenuItemId() {
        return menuItemId;
    }
    
    public String getName() {
        return name;
    }
    
    public Money getPrice() {
        return price;
    }
    
    public int getQuantity() {
        return quantity;
    }
    
    public Money getTotal() {
        return total;
    }
}
