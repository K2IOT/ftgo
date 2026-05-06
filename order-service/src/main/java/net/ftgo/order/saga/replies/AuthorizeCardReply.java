package net.ftgo.order.saga.replies;

/**
 * Reply from Accounting Service after authorizing a credit card.
 * 
 * Contains the authorization ID, which is stored in the saga data
 * for potential future operations (reversal, revision).
 */
public class AuthorizeCardReply {
    
    private Long authorizationId;
    
    /**
     * Default constructor for serialization.
     */
    public AuthorizeCardReply() {
    }
    
    /**
     * Creates an authorize card reply.
     * 
     * @param authorizationId the ID of the created authorization
     */
    public AuthorizeCardReply(Long authorizationId) {
        this.authorizationId = authorizationId;
    }
    
    public Long getAuthorizationId() {
        return authorizationId;
    }
    
    public void setAuthorizationId(Long authorizationId) {
        this.authorizationId = authorizationId;
    }
}
