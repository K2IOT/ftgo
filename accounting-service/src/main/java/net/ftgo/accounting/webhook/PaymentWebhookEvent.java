package net.ftgo.accounting.webhook;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(
    name = "payment_webhook_events",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_payment_webhook_provider_event",
        columnNames = {"provider", "provider_event_id"}
    )
)
public class PaymentWebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String provider;

    @Column(name = "provider_event_id", nullable = false, length = 255)
    private String providerEventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private PaymentWebhookType eventType;

    @Column(name = "provider_authorization_id", length = 255)
    private String providerAuthorizationId;

    @Column(name = "payload_hash", nullable = false, length = 80)
    private String payloadHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private PaymentWebhookApplyResult outcome;

    @Column(name = "provider_occurred_at")
    private Instant providerOccurredAt;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    protected PaymentWebhookEvent() {
    }

    public PaymentWebhookEvent(
        String provider,
        PaymentWebhookPayload payload,
        String payloadHash,
        PaymentWebhookApplyResult outcome
    ) {
        this.provider = requireText(provider, "provider");
        if (payload == null) throw new IllegalArgumentException("Webhook payload is required");
        this.providerEventId = requireText(payload.getEventId(), "providerEventId");
        if (payload.getType() == null) throw new IllegalArgumentException("Webhook type is required");
        this.eventType = payload.getType();
        this.providerAuthorizationId = payload.getProviderAuthorizationId();
        this.payloadHash = requireText(payloadHash, "payloadHash");
        if (outcome == null) throw new IllegalArgumentException("Webhook outcome is required");
        this.outcome = outcome;
        this.providerOccurredAt = payload.getOccurredAt();
        this.receivedAt = Instant.now();
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be null or blank");
        }
        return value;
    }

    @PrePersist
    protected void onCreate() {
        if (receivedAt == null) receivedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getProvider() { return provider; }
    public String getProviderEventId() { return providerEventId; }
    public PaymentWebhookType getEventType() { return eventType; }
    public String getProviderAuthorizationId() { return providerAuthorizationId; }
    public String getPayloadHash() { return payloadHash; }
    public PaymentWebhookApplyResult getOutcome() { return outcome; }
    public Instant getProviderOccurredAt() { return providerOccurredAt; }
    public Instant getReceivedAt() { return receivedAt; }

    /** Raw provider payloads are intentionally never retained. */
    public String getRawPayload() { return null; }
}
