package net.ftgo.order.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import net.ftgo.common.Money;

/**
 * Request DTO for an order line item.
 * 
 * Represents a menu item with quantity and price.
 */
public class OrderLineItemRequest {
    
    @NotNull(message = "Menu item ID is required")
    private final Long menuItemId;
    
    @NotBlank(message = "Item name is required")
    private final String name;
    
    @NotNull(message = "Price is required")
    private final Money price;
    
    @Positive(message = "Quantity must be positive")
    private final int quantity;
    
    @JsonCreator
    public OrderLineItemRequest(
            @JsonProperty("menuItemId") Long menuItemId,
            @JsonProperty("name") String name,
            @JsonProperty("price") Money price,
            @JsonProperty("quantity") int quantity) {
        this.menuItemId = menuItemId;
        this.name = name;
        this.price = price;
        this.quantity = quantity;
    }
    
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
}
