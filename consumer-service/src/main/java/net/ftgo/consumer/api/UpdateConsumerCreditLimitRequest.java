package net.ftgo.consumer.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public class UpdateConsumerCreditLimitRequest {

    @NotNull(message = "Credit limit is required")
    @Positive(message = "Credit limit must be positive")
    private final BigDecimal creditLimit;

    @JsonCreator
    public UpdateConsumerCreditLimitRequest(
        @JsonProperty("creditLimit") BigDecimal creditLimit
    ) {
        this.creditLimit = creditLimit;
    }

    public BigDecimal getCreditLimit() {
        return creditLimit;
    }
}
