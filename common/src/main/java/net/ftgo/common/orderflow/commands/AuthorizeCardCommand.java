package net.ftgo.common.orderflow.commands;

import io.eventuate.tram.commands.common.Command;
import net.ftgo.common.Money;

/**
 * Requests an idempotent payment authorization for an order.
 *
 * <p>The payment token is optional only for rolling-upgrade compatibility with
 * legacy producers. New order flows must provide it so Accounting can invoke
 * the configured payment authorization gateway.</p>
 */
public class AuthorizeCardCommand implements Command {

    private Long consumerId;
    private Long orderId;
    private Money amount;
    private String paymentToken;
    private String requestId;

    public AuthorizeCardCommand() {
    }

    public AuthorizeCardCommand(Long consumerId, Money amount, String requestId) {
        this(consumerId, null, amount, null, requestId);
    }

    public AuthorizeCardCommand(Long consumerId, Long orderId, Money amount, String requestId) {
        this(consumerId, orderId, amount, null, requestId);
    }

    public AuthorizeCardCommand(
        Long consumerId,
        Long orderId,
        Money amount,
        String paymentToken,
        String requestId
    ) {
        this.consumerId = consumerId;
        this.orderId = orderId;
        this.amount = amount;
        this.paymentToken = paymentToken;
        this.requestId = requestId;
    }

    public Long getConsumerId() { return consumerId; }
    public void setConsumerId(Long consumerId) { this.consumerId = consumerId; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Money getAmount() { return amount; }
    public void setAmount(Money amount) { this.amount = amount; }
    public String getPaymentToken() { return paymentToken; }
    public void setPaymentToken(String paymentToken) { this.paymentToken = paymentToken; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
