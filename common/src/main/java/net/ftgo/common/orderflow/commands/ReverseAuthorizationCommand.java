package net.ftgo.common.orderflow.commands;

import io.eventuate.tram.commands.common.Command;

public class ReverseAuthorizationCommand implements Command {

    private Long consumerId;
    private Long authorizationId;

    public ReverseAuthorizationCommand() {
    }

    public ReverseAuthorizationCommand(Long consumerId, Long authorizationId) {
        this.consumerId = consumerId;
        this.authorizationId = authorizationId;
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
}
