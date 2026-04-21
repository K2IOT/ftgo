package net.ftgo.consumer.messaging;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Reply message indicating successful consumer verification.
 */
public class ConsumerVerified {
    
    @JsonProperty("consumerId")
    private final Long consumerId;
    
    @JsonCreator
    public ConsumerVerified(@JsonProperty("consumerId") Long consumerId) {
        this.consumerId = consumerId;
    }
    
    public Long getConsumerId() {
        return consumerId;
    }
}
