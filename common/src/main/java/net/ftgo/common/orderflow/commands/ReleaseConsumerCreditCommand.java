package net.ftgo.common.orderflow.commands;

import io.eventuate.tram.commands.common.Command;

public class ReleaseConsumerCreditCommand implements Command {

    private Long consumerId;
    private Long orderId;
    private String reason;

    public ReleaseConsumerCreditCommand() {
    }

    public ReleaseConsumerCreditCommand(Long consumerId, Long orderId, String reason) {
        this.consumerId = consumerId;
        this.orderId = orderId;
        this.reason = reason;
    }

    public Long getConsumerId() { return consumerId; }
    public void setConsumerId(Long consumerId) { this.consumerId = consumerId; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
