package net.ftgo.orderhistory.messaging;

import java.math.BigDecimal;

/**
 * Domain event published when a credit card is authorized.
 * 
 * Published by Accounting Service to net.ftgo.accountingservice.domain.Account topic.
 */
public class CardAuthorizedEvent {
    
    private Long accountId;
    private Long authorizationId;
    private String requestId;
    private BigDecimal amount;
    private Long orderId; // Added for correlation with orders
    
    /**
     * Default constructor for JSON deserialization.
     */
    public CardAuthorizedEvent() {
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
    
    public Long getOrderId() {
        return orderId;
    }
    
    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }
}
