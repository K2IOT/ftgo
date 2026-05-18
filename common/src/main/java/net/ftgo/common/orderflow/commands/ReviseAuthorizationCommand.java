package net.ftgo.common.orderflow.commands;

import io.eventuate.tram.commands.common.Command;

import java.math.BigDecimal;

public class ReviseAuthorizationCommand implements Command {

    private Long consumerId;
    private Long authorizationId;
    private BigDecimal newAmount;
    private String requestId;

    public ReviseAuthorizationCommand() {
    }

    public ReviseAuthorizationCommand(Long consumerId, Long authorizationId, BigDecimal newAmount, String requestId) {
        this.consumerId = consumerId;
        this.authorizationId = authorizationId;
        this.newAmount = newAmount;
        this.requestId = requestId;
    }

    public Long getConsumerId() {
        return consumerId;
    }

    public void setConsumerId(Long consumerId) {
        this.consumerId = consumerId;
    }

    public Long getAuthorizationId() {
        return authorizationId;
    }

    public void setAuthorizationId(Long authorizationId) {
        this.authorizationId = authorizationId;
    }

    public BigDecimal getNewAmount() {
        return newAmount;
    }

    public void setNewAmount(BigDecimal newAmount) {
        this.newAmount = newAmount;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
}
