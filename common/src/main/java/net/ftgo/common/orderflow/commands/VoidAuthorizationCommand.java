package net.ftgo.common.orderflow.commands;

import io.eventuate.tram.commands.common.Command;

public class VoidAuthorizationCommand implements Command {

    private Long orderId;
    private Long authorizationId;
    private String reason;
    private String requestId;

    public VoidAuthorizationCommand() {
    }

    public VoidAuthorizationCommand(Long orderId, Long authorizationId, String reason, String requestId) {
        this.orderId = orderId;
        this.authorizationId = authorizationId;
        this.reason = reason;
        this.requestId = requestId;
    }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getAuthorizationId() { return authorizationId; }
    public void setAuthorizationId(Long authorizationId) { this.authorizationId = authorizationId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
