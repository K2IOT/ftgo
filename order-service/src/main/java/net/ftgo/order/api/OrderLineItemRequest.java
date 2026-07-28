package net.ftgo.order.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import net.ftgo.common.Money;
import net.ftgo.common.web.RequestLimits;

/** Request DTO for an order line item. */
public class OrderLineItemRequest {

    @NotNull(message = "Menu item ID is required")
    @Positive(message = "Menu item ID must be positive")
    private final Long menuItemId;

    @NotBlank(message = "Item name is required")
    @Size(max = RequestLimits.MAX_TEXT_LENGTH, message = "Item name is too long")
    private final String name;

    @NotNull(message = "Price is required")
    private final Money price;

    @Min(value = 1, message = "Quantity must be at least 1")
    @Max(value = RequestLimits.MAX_ITEM_QUANTITY, message = "Quantity must not exceed 100")
    private final int quantity;

    @JsonCreator
    public OrderLineItemRequest(
        @JsonProperty("menuItemId") Long menuItemId,
        @JsonProperty("name") String name,
        @JsonProperty("price") Money price,
        @JsonProperty("quantity") int quantity
    ) {
        this.menuItemId = menuItemId;
        this.name = name;
        this.price = price;
        this.quantity = quantity;
    }

    public Long getMenuItemId() { return menuItemId; }
    public String getName() { return name; }
    public Money getPrice() { return price; }
    public int getQuantity() { return quantity; }
}
