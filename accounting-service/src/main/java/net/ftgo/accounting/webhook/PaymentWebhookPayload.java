package net.ftgo.accounting.webhook;

import net.ftgo.common.Money;

import java.time.Instant;

/** Provider-neutral payload parsed only after raw-body signature verification. */
public class PaymentWebhookPayload {

    private String eventId;
    private PaymentWebhookType type;
    private String providerAuthorizationId;
    private String providerCaptureId;
    private String providerRefundId;
    private Money amount;
    private Instant occurredAt;

    public PaymentWebhookPayload() {
    }

    public PaymentWebhookPayload(
        String eventId,
        PaymentWebhookType type,
        String providerAuthorizationId,
        String providerCaptureId,
        String providerRefundId,
        Money amount,
        Instant occurredAt
    ) {
        this.eventId = eventId;
        this.type = type;
        this.providerAuthorizationId = providerAuthorizationId;
        this.providerCaptureId = providerCaptureId;
        this.providerRefundId = providerRefundId;
        this.amount = amount;
        this.occurredAt = occurredAt;
    }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public PaymentWebhookType getType() { return type; }
    public void setType(PaymentWebhookType type) { this.type = type; }
    public String getProviderAuthorizationId() { return providerAuthorizationId; }
    public void setProviderAuthorizationId(String providerAuthorizationId) {
        this.providerAuthorizationId = providerAuthorizationId;
    }
    public String getProviderCaptureId() { return providerCaptureId; }
    public void setProviderCaptureId(String providerCaptureId) {
        this.providerCaptureId = providerCaptureId;
    }
    public String getProviderRefundId() { return providerRefundId; }
    public void setProviderRefundId(String providerRefundId) {
        this.providerRefundId = providerRefundId;
    }
    public Money getAmount() { return amount; }
    public void setAmount(Money amount) { this.amount = amount; }
    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
}
