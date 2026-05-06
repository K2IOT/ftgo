package net.ftgo.order.saga.commands;

import io.eventuate.tram.commands.common.Command;

import java.math.BigDecimal;

/**
 * Command to revise a credit card authorization to a new amount.
 * 
 * Sent by ReviseOrderSaga to Accounting Service to adjust payment authorization
 * when an order is revised.
 * 
 * This command is idempotent - if the authorization has already been revised to
 * the specified amount, the command will succeed without making changes.
 * 
 * Field types must match the Accounting Service's handler-side command:
 * - consumerId: Long (required for account lookup)
 * - authorizationId: Long (matches Authorization entity's ID type)
 * - newAmount: BigDecimal
 */
public class ReviseAuthorizationCommand implements Command {
    
    private Long consumerId;
    private Long authorizationId;
    private BigDecimal newAmount;
    
    /**
     * Default constructor for JSON deserialization.
     */
    public ReviseAuthorizationCommand() {
    }
    
    /**
     * Creates a new ReviseAuthorizationCommand.
     * 
     * @param consumerId the consumer ID (for account lookup)
     * @param authorizationId the ID of the authorization to revise
     * @param newAmount the new authorization amount
     */
    public ReviseAuthorizationCommand(Long consumerId, Long authorizationId, BigDecimal newAmount) {
        this.consumerId = consumerId;
        this.authorizationId = authorizationId;
        this.newAmount = newAmount;
    }
    
    public Long getConsumerId() {
        return consumerId;
    }
    
    public void setConsumerId(Long consumerId) {
        this.consumerId = consumerId;
    }
    
    public Long getAuthorizationId() {
        return authorizationId;
    }
    
    public void setAuthorizationId(Long authorizationId) {
        this.authorizationId = authorizationId;
    }
    
    public BigDecimal getNewAmount() {
        return newAmount;
    }
    
    public void setNewAmount(BigDecimal newAmount) {
        this.newAmount = newAmount;
    }
    
    @Override
    public String toString() {
        return String.format("ReviseAuthorizationCommand{consumerId=%d, authorizationId=%d, newAmount=%s}",
            consumerId, authorizationId, newAmount);
    }
}
