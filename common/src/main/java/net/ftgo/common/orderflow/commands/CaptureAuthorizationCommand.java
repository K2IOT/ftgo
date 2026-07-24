package net.ftgo.common.orderflow.commands;

import io.eventuate.tram.commands.common.Command;

public class CaptureAuthorizationCommand implements Command {

    private Long orderId;
    private Long authorizationId;
    private String requestId;

    public CaptureAuthorizationCommand() {
    }

    public CaptureAuthorizationCommand(Long orderId, Long authorizationId, String requestId) {
        this.orderId = orderId;
        this.authorizationId = authorizationId;
        this.requestId = requestId;
    }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getAuthorizationId() { return authorizationId; }
    public void setAuthorizationId(Long authorizationId) { this.authorizationId = authorizationId; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
