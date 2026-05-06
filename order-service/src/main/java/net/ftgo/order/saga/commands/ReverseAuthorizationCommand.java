package net.ftgo.order.saga.commands;

import io.eventuate.tram.commands.common.Command;

/**
 * Command to reverse a credit card authorization in Accounting Service.
 * 
 * This is the PIVOT POINT in CancelOrderSaga. Once this step succeeds,
 * the saga must complete forward (no compensation). All subsequent steps
 * are retriable.
 * 
 * Field types must match the Accounting Service's handler-side command:
 * - consumerId: Long (required for account lookup)
 * - authorizationId: Long (matches Authorization entity's ID type)
 */
public class ReverseAuthorizationCommand implements Command {
    
    private Long consumerId;
    private Long authorizationId;
    
    /**
     * Default constructor for serialization.
     */
    public ReverseAuthorizationCommand() {
    }
    
    /**
     * Creates a command to reverse an authorization.
     * 
     * @param consumerId the consumer ID (for account lookup)
     * @param authorizationId the authorization ID to reverse
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
    
    @Override
    public String toString() {
        return String.format("ReverseAuthorizationCommand{consumerId=%d, authorizationId=%d}",
            consumerId, authorizationId);
    }
}
