package net.ftgo.common.orderflow.commands;

import io.eventuate.tram.commands.common.Command;

public class CommitConsumerCreditCommand implements Command {

    private Long consumerId;
    private Long orderId;

    public CommitConsumerCreditCommand() {
    }

    public CommitConsumerCreditCommand(Long consumerId, Long orderId) {
        this.consumerId = consumerId;
        this.orderId = orderId;
    }

    public Long getConsumerId() { return consumerId; }
    public void setConsumerId(Long consumerId) { this.consumerId = consumerId; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
}
