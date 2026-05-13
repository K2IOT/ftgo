package net.ftgo.common.orderflow.commands;

import io.eventuate.tram.commands.common.Command;
import net.ftgo.common.Money;

public class AuthorizeCardCommand implements Command {

    private Long consumerId;
    private Money amount;
    private String requestId;

    public AuthorizeCardCommand() {
    }

    public AuthorizeCardCommand(Long consumerId, Money amount, String requestId) {
        this.consumerId = consumerId;
        this.amount = amount;
        this.requestId = requestId;
    }

    public Long getConsumerId() {
        return consumerId;
    }

    public void setConsumerId(Long consumerId) {
        this.consumerId = consumerId;
    }

    public Money getAmount() {
        return amount;
    }

    public void setAmount(Money amount) {
        this.amount = amount;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
}
