package net.ftgo.accounting.messaging;

/**
 * Reply message indicating successful authorization reversal.
 * 
 * Returned by reverseAuthorization command handler to CancelOrderSaga.
 */
public class AuthorizationReversed {
    
    private Long authorizationId;
    
    /**
     * Default constructor for JSON deserialization.
     */
    public AuthorizationReversed() {
    }
    
    /**
     * Creates a new AuthorizationReversed reply.
     * 
     * @param authorizationId the ID of the reversed authorization
     */
    public AuthorizationReversed(Long authorizationId) {
        this.authorizationId = authorizationId;
    }
    
    public Long getAuthorizationId() {
        return authorizationId;
    }
    
    public void setAuthorizationId(Long authorizationId) {
        this.authorizationId = authorizationId;
    }
}
