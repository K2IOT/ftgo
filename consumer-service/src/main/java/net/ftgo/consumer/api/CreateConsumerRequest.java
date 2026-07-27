package net.ftgo.consumer.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for consumer registration. Privileged financial fields are
 * intentionally ignored; the server assigns the initial credit policy.
 */
@JsonIgnoreProperties(value = "creditLimit")
public class CreateConsumerRequest {

    @NotBlank(message = "Name is required")
    private final String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private final String email;

    @JsonCreator
    public CreateConsumerRequest(
        @JsonProperty("name") String name,
        @JsonProperty("email") String email
    ) {
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
