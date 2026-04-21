package net.ftgo.accounting.messaging;

/**
 * Reply message indicating successful credit card authorization.
 * 
 * Returned by authorizeCard command handler to CreateOrderSaga.
 */
public class CardAuthorized {
    
    private Long authorizationId;
    
    /**
     * Default constructor for JSON deserialization.
     */
    public CardAuthorized() {
    }
    
    /**
     * Creates a new CardAuthorized reply.
     * 
     * @param authorizationId the ID of the created authorization
     */
    public CardAuthorized(Long authorizationId) {
        this.authorizationId = authorizationId;
    }
    
    public Long getAuthorizationId() {
        return authorizationId;
    }
    
    public void setAuthorizationId(Long authorizationId) {
        this.authorizationId = authorizationId;
    }
}
