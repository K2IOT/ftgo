package net.ftgo.common.orderflow.commands;

import io.eventuate.tram.commands.common.Command;
import net.ftgo.common.Money;

public class ReserveConsumerCreditCommand implements Command {

    private Long consumerId;
    private Long orderId;
    private Money amount;

    public ReserveConsumerCreditCommand() {
    }

    public ReserveConsumerCreditCommand(Long consumerId, Long orderId, Money amount) {
        this.consumerId = consumerId;
        this.orderId = orderId;
        this.amount = amount;
    }

    public Long getConsumerId() { return consumerId; }
    public void setConsumerId(Long consumerId) { this.consumerId = consumerId; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Money getAmount() { return amount; }
    public void setAmount(Money amount) { this.amount = amount; }
}
