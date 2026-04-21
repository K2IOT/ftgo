package net.ftgo.consumer.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;

/**
 * Request DTO for updating consumer profile information.
 */
public class UpdateConsumerRequest {
    
    private final String name;
    
    @Email(message = "Email must be valid")
    private final String email;
    
    @JsonCreator
    public UpdateConsumerRequest(
            @JsonProperty("name") String name,
            @JsonProperty("email") String email) {
        this.name = name;
        this.email = email;
    }
    
    public String getName() {
        return name;
    }
    
    public String getEmail() {
        return email;
    }
}
