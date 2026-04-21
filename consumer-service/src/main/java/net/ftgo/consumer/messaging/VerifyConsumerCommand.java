package net.ftgo.consumer.messaging;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.eventuate.tram.commands.common.Command;

import java.math.BigDecimal;

/**
 * Command to verify consumer credit limit for an order.
 * 
 * Sent by CreateOrderSaga to Consumer Service to validate that:
 * 1. Consumer exists
 * 2. Consumer has sufficient available credit for the order total
 */
public class VerifyConsumerCommand implements Command {
    
    @JsonProperty("consumerId")
    private final Long consumerId;
    
    @JsonProperty("orderTotal")
    private final BigDecimal orderTotal;
    
    @JsonCreator
    public VerifyConsumerCommand(
            @JsonProperty("consumerId") Long consumerId,
            @JsonProperty("orderTotal") BigDecimal orderTotal) {
        this.consumerId = consumerId;
        this.orderTotal = orderTotal;
    }
    
    public Long getConsumerId() {
        return consumerId;
    }
    
    public BigDecimal getOrderTotal() {
        return orderTotal;
    }
}
