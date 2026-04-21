package net.ftgo.consumer.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Domain event published when a consumer profile is updated.
 * 
 * This event is published via the Transactional Outbox pattern to ensure
 * reliable event delivery to interested services.
 */
public class ConsumerUpdated {
    
    @JsonProperty("consumerId")
    private final Long consumerId;
    
    @JsonProperty("name")
    private final String name;
    
    @JsonProperty("email")
    private final String email;
    
    @JsonProperty("creditLimit")
    private final BigDecimal creditLimit;
    
    @JsonProperty("availableCredit")
    private final BigDecimal availableCredit;
    
    @JsonProperty("updatedAt")
    private final LocalDateTime updatedAt;
    
    @JsonCreator
    public ConsumerUpdated(
            @JsonProperty("consumerId") Long consumerId,
            @JsonProperty("name") String name,
            @JsonProperty("email") String email,
            @JsonProperty("creditLimit") BigDecimal creditLimit,
            @JsonProperty("availableCredit") BigDecimal availableCredit,
            @JsonProperty("updatedAt") LocalDateTime updatedAt) {
        this.consumerId = consumerId;
        this.name = name;
        this.email = email;
        this.creditLimit = creditLimit;
        this.availableCredit = availableCredit;
        this.updatedAt = updatedAt;
    }
    
    public Long getConsumerId() {
        return consumerId;
    }
    
    public String getName() {
        return name;
    }
    
    public String getEmail() {
        return email;
    }
    
    public BigDecimal getCreditLimit() {
        return creditLimit;
    }
    
    public BigDecimal getAvailableCredit() {
        return availableCredit;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
