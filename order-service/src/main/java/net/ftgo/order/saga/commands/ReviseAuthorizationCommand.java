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
 */
public class ReviseAuthorizationCommand implements Command {
    
    private String authorizationId;
    private BigDecimal newAmount;
    
    /**
     * Default constructor for JSON deserialization.
     */
    public ReviseAuthorizationCommand() {
    }
    
    /**
     * Creates a new ReviseAuthorizationCommand.
     * 
     * @param authorizationId the ID of the authorization to revise
     * @param newAmount the new authorization amount
     */
    public ReviseAuthorizationCommand(String authorizationId, BigDecimal newAmount) {
        this.authorizationId = authorizationId;
        this.newAmount = newAmount;
    }
    
    public String getAuthorizationId() {
        return authorizationId;
    }
    
    public void setAuthorizationId(String authorizationId) {
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
        return String.format("ReviseAuthorizationCommand{authorizationId=%s, newAmount=%s}",
            authorizationId, newAmount);
    }
}
