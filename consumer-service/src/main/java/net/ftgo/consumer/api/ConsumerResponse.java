package net.ftgo.consumer.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import net.ftgo.consumer.domain.Consumer;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response DTO for consumer data.
 */
public class ConsumerResponse {
    
    @JsonProperty("id")
    private final Long id;
    
    @JsonProperty("name")
    private final String name;
    
    @JsonProperty("email")
    private final String email;
    
    @JsonProperty("creditLimit")
    private final BigDecimal creditLimit;
    
    @JsonProperty("availableCredit")
    private final BigDecimal availableCredit;
    
    @JsonProperty("createdAt")
    private final LocalDateTime createdAt;
    
    @JsonProperty("updatedAt")
    private final LocalDateTime updatedAt;
    
    public ConsumerResponse(Consumer consumer) {
        this.id = consumer.getId();
        this.name = consumer.getName();
        this.email = consumer.getEmail();
        this.creditLimit = consumer.getCreditLimit().getAmount();
        this.availableCredit = consumer.getAvailableCredit().getAmount();
        this.createdAt = consumer.getCreatedAt();
        this.updatedAt = consumer.getUpdatedAt();
    }
    
    public Long getId() {
        return id;
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
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
