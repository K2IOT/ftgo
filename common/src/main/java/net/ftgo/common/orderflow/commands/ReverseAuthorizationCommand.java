package net.ftgo.common.orderflow.commands;

import io.eventuate.tram.commands.common.Command;

public class ReverseAuthorizationCommand implements Command {

    private Long consumerId;
    private Long authorizationId;
    private Long orderId;
    private String requestId;

    public ReverseAuthorizationCommand() {
    }

    /** Legacy constructor retained for rolling compatibility. */
    public ReverseAuthorizationCommand(Long consumerId, Long authorizationId) {
        this(consumerId, authorizationId, null, null);
    }

    public ReverseAuthorizationCommand(
        Long consumerId,
        Long authorizationId,
        Long orderId,
        String requestId
    ) {
        this.consumerId = consumerId;
        this.authorizationId = authorizationId;
        this.orderId = orderId;
        this.requestId = requestId;
    }

    public Long getConsumerId() { return consumerId; }
    public void setConsumerId(Long consumerId) { this.consumerId = consumerId; }
    public Long getAuthorizationId() { return authorizationId; }
    public void setAuthorizationId(Long authorizationId) { this.authorizationId = authorizationId; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}