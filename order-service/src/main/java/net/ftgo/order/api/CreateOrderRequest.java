package net.ftgo.order.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import net.ftgo.common.Address;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Request DTO for creating an order from a specific restaurant menu snapshot.
 * Consumer identity is derived from the authenticated principal and is never
 * accepted from the request body.
 */
@JsonIgnoreProperties(value = "consumerId")
public class CreateOrderRequest {

    @NotNull(message = "Restaurant ID is required")
    @Positive(message = "Restaurant ID must be positive")
    private final Long restaurantId;

    @PositiveOrZero(message = "Expected menu version must be zero or positive")
    private final Long expectedMenuVersion;

    @NotNull(message = "Order line items are required")
    @Size(min = 1, max = 50, message = "Order must contain between 1 and 50 line items")
    @Valid
    private final List<OrderLineItemRequest> lineItems;

    @NotNull(message = "Delivery address is required")
    @Valid
    private final Address deliveryAddress;

    @NotNull(message = "Delivery time is required")
    @Future(message = "Delivery time must be in the future")
    private final LocalDateTime deliveryTime;

    @NotBlank(message = "Payment token is required")
    @Size(max = 255, message = "Payment token must not exceed 255 characters")
    private final String paymentToken;

    /**
     * Backward-compatible constructor for Java callers that do not provide a
     * menu version. The consumer identity still comes from authentication.
     */
    public CreateOrderRequest(
        Long restaurantId,
        List<OrderLineItemRequest> lineItems,
        Address deliveryAddress,
        LocalDateTime deliveryTime,
        String paymentToken
    ) {
        this(
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
        @JsonProperty("restaurantId") Long restaurantId,
        @JsonProperty("expectedMenuVersion") Long expectedMenuVersion,
        @JsonProperty("lineItems") List<OrderLineItemRequest> lineItems,
        @JsonProperty("deliveryAddress") Address deliveryAddress,
        @JsonProperty("deliveryTime") LocalDateTime deliveryTime,
        @JsonProperty("paymentToken") String paymentToken
    ) {
        this.restaurantId = restaurantId;
        this.expectedMenuVersion = expectedMenuVersion == null ? 0L : expectedMenuVersion;
        this.lineItems = lineItems;
        this.deliveryAddress = deliveryAddress;
        this.deliveryTime = deliveryTime;
        this.paymentToken = paymentToken;
    }

    public Long getRestaurantId() { return restaurantId; }
    public Long getExpectedMenuVersion() { return expectedMenuVersion; }
    public List<OrderLineItemRequest> getLineItems() { return lineItems; }
    public Address getDeliveryAddress() { return deliveryAddress; }
    public LocalDateTime getDeliveryTime() { return deliveryTime; }
    public String getPaymentToken() { return paymentToken; }
}
