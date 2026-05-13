package net.ftgo.common.orderflow.replies;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

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
