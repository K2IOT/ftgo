package net.ftgo.order.saga.commands;

import io.eventuate.tram.commands.common.Command;
import net.ftgo.common.Money;

/**
 * Command sent to Accounting Service to authorize a credit card payment.
 * 
 * Part of CreateOrderSaga step 4 (PIVOT POINT).
 * This is the first non-compensatable step in the saga.
 * Once authorization succeeds, the saga must complete forward.
 * 
 * Accounting Service:
 * - Authorizes the credit card for the order total
 * - Returns SUCCESS or FAILURE
 * - Implements idempotent processing using requestId
 */
public class AuthorizeCardCommand implements Command {
    
    private Long consumerId;
    private Money amount;
    private String requestId; // Idempotency key
    
    /**
     * Default constructor for serialization.
     */
    public AuthorizeCardCommand() {
    }
    
    /**
     * Creates an authorize card command.
     * 
     * @param consumerId the consumer ID
     * @param amount the amount to authorize
     * @param requestId the idempotency key (typically orderId)
     */
    public AuthorizeCardCommand(Long consumerId, Money amount, String requestId) {
        this.consumerId = consumerId;
        this.amount = amount;
        this.requestId = requestId;
    }
    
    public Long getConsumerId() {
        return consumerId;
    }
    
    public void setConsumerId(Long consumerId) {
        this.consumerId = consumerId;
    }
    
    public Money getAmount() {
        return amount;
    }
    
    public void setAmount(Money amount) {
        this.amount = amount;
    }
    
    public String getRequestId() {
        return requestId;
    }
    
    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
}
