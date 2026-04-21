package net.ftgo.accounting.messaging;

import io.eventuate.tram.commands.common.Command;

import java.math.BigDecimal;

/**
 * Command to authorize a credit card transaction.
 * 
 * Sent by CreateOrderSaga to authorize payment for an order.
 * Uses requestId as idempotency key to ensure duplicate requests return cached results.
 */
public class AuthorizeCardCommand implements Command {
    
    private Long consumerId;
    private String requestId; // Idempotency key
    private BigDecimal amount;
    
    /**
     * Default constructor for JSON deserialization.
     */
    public AuthorizeCardCommand() {
    }
    
    /**
     * Creates a new AuthorizeCardCommand.
     * 
     * @param consumerId the consumer ID
     * @param requestId the unique request ID (idempotency key)
     * @param amount the amount to authorize
     */
    public AuthorizeCardCommand(Long consumerId, String requestId, BigDecimal amount) {
        this.consumerId = consumerId;
        this.requestId = requestId;
        this.amount = amount;
    }
    
    public Long getConsumerId() {
        return consumerId;
    }
    
    public void setConsumerId(Long consumerId) {
        this.consumerId = consumerId;
    }
    
    public String getRequestId() {
        return requestId;
    }
    
    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
    
    public BigDecimal getAmount() {
        return amount;
    }
    
    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
}
