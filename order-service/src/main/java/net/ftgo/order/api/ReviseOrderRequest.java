package net.ftgo.order.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Request DTO for revising an existing order.
 * 
 * Contains the revised line items for the order.
 */
public class ReviseOrderRequest {
    
    @NotEmpty(message = "Revised order must have at least one line item")
    @Valid
    private final List<OrderLineItemRequest> revisedLineItems;
    
    @JsonCreator
    public ReviseOrderRequest(
            @JsonProperty("revisedLineItems") List<OrderLineItemRequest> revisedLineItems) {
        this.revisedLineItems = revisedLineItems;
    }
    
    public List<OrderLineItemRequest> getRevisedLineItems() {
        return revisedLineItems;
    }
}
