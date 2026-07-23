package net.ftgo.order.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import net.ftgo.common.Address;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Request DTO for creating an order from a specific restaurant menu snapshot.
 */
public class CreateOrderRequest {

    @NotNull(message = "Consumer ID is required")
    private final Long consumerId;

    @NotNull(message = "Restaurant ID is required")
    private final Long restaurantId;

    @PositiveOrZero(message = "Expected menu version must be zero or positive")
    private final Long expectedMenuVersion;

    @NotEmpty(message = "Order must have at least one line item")
    @Valid
    private final List<OrderLineItemRequest> lineItems;

    @NotNull(message = "Delivery address is required")
    @Valid
    private final Address deliveryAddress;

    @NotNull(message = "Delivery time is required")
    private final LocalDateTime deliveryTime;

    @NotNull(message = "Payment token is required")
    private final String paymentToken;

    /**
     * Backward-compatible constructor for existing Java callers. JSON callers
     * should send expectedMenuVersion from the menu snapshot they rendered.
     */
    public CreateOrderRequest(
        Long consumerId,
        Long restaurantId,
        List<OrderLineItemRequest> lineItems,
        Address deliveryAddress,
        LocalDateTime deliveryTime,
        String paymentToken
    ) {
        this(
            consumerId,
            restaurantId,
            0L,
            lineItems,
            deliveryAddress,
            deliveryTime,
            paymentToken
        );
    }

    @JsonCreator
    public CreateOrderRequest(
        @JsonProperty("consumerId") Long consumerId,
        @JsonProperty("restaurantId") Long restaurantId,
        @JsonProperty("expectedMenuVersion") Long expectedMenuVersion,
        @JsonProperty("lineItems") List<OrderLineItemRequest> lineItems,
        @JsonProperty("deliveryAddress") Address deliveryAddress,
        @JsonProperty("deliveryTime") LocalDateTime deliveryTime,
        @JsonProperty("paymentToken") String paymentToken
    ) {
        this.consumerId = consumerId;
        this.restaurantId = restaurantId;
        this.expectedMenuVersion = expectedMenuVersion == null ? 0L : expectedMenuVersion;
        this.lineItems = lineItems;
        this.deliveryAddress = deliveryAddress;
        this.deliveryTime = deliveryTime;
        this.paymentToken = paymentToken;
    }

    public Long getConsumerId() { return consumerId; }
    public Long getRestaurantId() { return restaurantId; }
    public Long getExpectedMenuVersion() { return expectedMenuVersion; }
    public List<OrderLineItemRequest> getLineItems() { return lineItems; }
    public Address getDeliveryAddress() { return deliveryAddress; }
    public LocalDateTime getDeliveryTime() { return deliveryTime; }
    public String getPaymentToken() { return paymentToken; }
}
