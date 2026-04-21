package net.ftgo.accounting.messaging;

/**
 * Reply message indicating successful authorization revision.
 * 
 * Returned by reviseAuthorization command handler to ReviseOrderSaga.
 */
public class AuthorizationRevised {
    
    private Long authorizationId;
    
    /**
     * Default constructor for JSON deserialization.
     */
    public AuthorizationRevised() {
    }
    
    /**
     * Creates a new AuthorizationRevised reply.
     * 
     * @param authorizationId the ID of the revised authorization
     */
    public AuthorizationRevised(Long authorizationId) {
        this.authorizationId = authorizationId;
    }
    
    public Long getAuthorizationId() {
        return authorizationId;
    }
    
    public void setAuthorizationId(Long authorizationId) {
        this.authorizationId = authorizationId;
    }
}
