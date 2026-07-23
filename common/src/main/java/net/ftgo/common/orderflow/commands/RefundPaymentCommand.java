package net.ftgo.common.orderflow.commands;

import io.eventuate.tram.commands.common.Command;
import net.ftgo.common.Money;

public class RefundPaymentCommand implements Command {

    private Long orderId;
    private Long captureId;
    private Money amount;
    private String reason;
    private String requestId;

    public RefundPaymentCommand() {
    }

    public RefundPaymentCommand(Long orderId, Long captureId, Money amount, String reason, String requestId) {
        this.orderId = orderId;
        this.captureId = captureId;
        this.amount = amount;
        this.reason = reason;
        this.requestId = requestId;
    }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getCaptureId() { return captureId; }
    public void setCaptureId(Long captureId) { this.captureId = captureId; }
    public Money getAmount() { return amount; }
    public void setAmount(Money amount) { this.amount = amount; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
