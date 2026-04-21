package net.ftgo.accounting.messaging;

import io.eventuate.tram.commands.common.Command;

/**
 * Command to reverse a credit card authorization.
 * 
 * Sent by CancelOrderSaga to reverse payment authorization when an order is cancelled.
 */
public class ReverseAuthorizationCommand implements Command {
    
    private Long consumerId;
    private Long authorizationId;
    
    /**
     * Default constructor for JSON deserialization.
     */
    public ReverseAuthorizationCommand() {
    }
    
    /**
     * Creates a new ReverseAuthorizationCommand.
     * 
     * @param consumerId the consumer ID
     * @param authorizationId the ID of the authorization to reverse
     */
    public ReverseAuthorizationCommand(Long consumerId, Long authorizationId) {
        this.consumerId = consumerId;
        this.authorizationId = authorizationId;
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
}
