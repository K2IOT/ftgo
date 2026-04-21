package net.ftgo.accounting.messaging;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Domain event published when a credit card is successfully authorized.
 * 
 * Published to net.ftgo.accountingservice.domain.Account topic via Transactional Outbox.
 */
public class CardAuthorizedEvent {
    
    private Long accountId;
    private Long authorizationId;
    private String requestId;
    private BigDecimal amount;
    private LocalDateTime authorizedAt;
    
    /**
     * Default constructor for JSON deserialization.
     */
    public CardAuthorizedEvent() {
    }
    
    /**
     * Creates a new CardAuthorizedEvent.
     * 
     * @param accountId the account ID
     * @param authorizationId the ID of the created authorization
     * @param requestId the request ID (idempotency key)
     * @param amount the authorized amount
     * @param authorizedAt the timestamp when the authorization was created
     */
    public CardAuthorizedEvent(Long accountId, Long authorizationId, String requestId, 
                               BigDecimal amount, LocalDateTime authorizedAt) {
        this.accountId = accountId;
        this.authorizationId = authorizationId;
        this.requestId = requestId;
        this.amount = amount;
        this.authorizedAt = authorizedAt;
    }
    
    public Long getAccountId() {
        return accountId;
    }
    
    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }
    
    public Long getAuthorizationId() {
        return authorizationId;
    }
    
    public void setAuthorizationId(Long authorizationId) {
        this.authorizationId = authorizationId;
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
    
    public LocalDateTime getAuthorizedAt() {
        return authorizedAt;
    }
    
    public void setAuthorizedAt(LocalDateTime authorizedAt) {
        this.authorizedAt = authorizedAt;
    }
}
