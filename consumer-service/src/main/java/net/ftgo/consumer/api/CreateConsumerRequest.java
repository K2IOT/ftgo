package net.ftgo.consumer.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * Request DTO for creating a new consumer account.
 */
public class CreateConsumerRequest {
    
    @NotBlank(message = "Name is required")
    private final String name;
    
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private final String email;
    
    @NotNull(message = "Credit limit is required")
    @Positive(message = "Credit limit must be positive")
    private final BigDecimal creditLimit;
    
    @JsonCreator
    public CreateConsumerRequest(
            @JsonProperty("name") String name,
            @JsonProperty("email") String email,
            @JsonProperty("creditLimit") BigDecimal creditLimit) {
        this.name = name;
        this.email = email;
        this.creditLimit = creditLimit;
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
}
