package net.ftgo.accounting.messaging;

import java.time.LocalDateTime;

/**
 * Domain event published when a credit card authorization is reversed.
 * 
 * Published to net.ftgo.accountingservice.domain.Account topic via Transactional Outbox.
 */
public class CardReversed {
    
    private Long accountId;
    private Long authorizationId;
    private LocalDateTime reversedAt;
    
    /**
     * Default constructor for JSON deserialization.
     */
    public CardReversed() {
    }
    
    /**
     * Creates a new CardReversed event.
     * 
     * @param accountId the account ID
     * @param authorizationId the ID of the reversed authorization
     * @param reversedAt the timestamp when the authorization was reversed
     */
    public CardReversed(Long accountId, Long authorizationId, LocalDateTime reversedAt) {
        this.accountId = accountId;
        this.authorizationId = authorizationId;
        this.reversedAt = reversedAt;
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
    
    public LocalDateTime getReversedAt() {
        return reversedAt;
    }
    
    public void setReversedAt(LocalDateTime reversedAt) {
        this.reversedAt = reversedAt;
    }
}
