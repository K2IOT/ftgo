package net.ftgo.common.orderflow.commands;

import io.eventuate.tram.commands.common.Command;
import net.ftgo.common.Money;

public class VerifyConsumerCommand implements Command {

    private Long consumerId;
    private Money orderTotal;

    public VerifyConsumerCommand() {
    }

    public VerifyConsumerCommand(Long consumerId, Money orderTotal) {
        this.consumerId = consumerId;
        this.orderTotal = orderTotal;
    }

    public Long getConsumerId() {
        return consumerId;
    }

    public void setConsumerId(Long consumerId) {
        this.consumerId = consumerId;
    }

    public Money getOrderTotal() {
        return orderTotal;
    }

    public void setOrderTotal(Money orderTotal) {
        this.orderTotal = orderTotal;
    }
}
