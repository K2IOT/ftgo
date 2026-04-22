package net.ftgo.order.saga.commands;

import io.eventuate.tram.commands.common.Command;

/**
 * Command to reverse a credit card authorization in Accounting Service.
 * 
 * This is the PIVOT POINT in CancelOrderSaga. Once this step succeeds,
 * the saga must complete forward (no compensation). All subsequent steps
 * are retriable.
 */
public class ReverseAuthorizationCommand implements Command {
    
    private String authorizationId;
    
    /**
     * Default constructor for serialization.
     */
    public ReverseAuthorizationCommand() {
    }
    
    /**
     * Creates a command to reverse an authorization.
     * 
     * @param authorizationId the authorization ID to reverse
     */
    public ReverseAuthorizationCommand(String authorizationId) {
        this.authorizationId = authorizationId;
    }
    
    public String getAuthorizationId() {
        return authorizationId;
    }
    
    public void setAuthorizationId(String authorizationId) {
        this.authorizationId = authorizationId;
    }
    
    @Override
    public String toString() {
        return String.format("ReverseAuthorizationCommand{authorizationId=%s}", authorizationId);
    }
}
