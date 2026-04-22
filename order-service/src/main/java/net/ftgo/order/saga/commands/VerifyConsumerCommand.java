package net.ftgo.order.saga.commands;

import io.eventuate.tram.commands.common.Command;
import net.ftgo.common.Money;

/**
 * Command sent to Consumer Service to verify consumer credit limit.
 * 
 * Part of CreateOrderSaga step 2.
 * Consumer Service validates that:
 * - Consumer exists
 * - Order total does not exceed available credit limit
 */
public class VerifyConsumerCommand implements Command {
    
    private Long consumerId;
    private Money orderTotal;
    
    /**
     * Default constructor for serialization.
     */
    public VerifyConsumerCommand() {
    }
    
    /**
     * Creates a verify consumer command.
     * 
     * @param consumerId the consumer ID to verify
     * @param orderTotal the order total to check against credit limit
     */
    public VerifyConsumerCommand(Long consumerId, Money orderTotal) {
        this.consumerId = consumerId;
        this.orderTotal = orderTotal;
    }
    
    public Long getConsumerId() {
        return consumerId;
    }
    
    public void setConsumerId(Long consumerId) {
        this.consumerId = consumerId;
    }
    
    public Money getOrderTotal() {
        return orderTotal;
    }
    
    public void setOrderTotal(Money orderTotal) {
        this.orderTotal = orderTotal;
    }
}
