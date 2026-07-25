-- Phase 02B Task 6: signed, replay-safe and idempotent provider webhooks.
CREATE TABLE payment_webhook_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    provider VARCHAR(50) NOT NULL,
    provider_event_id VARCHAR(255) NOT NULL,
    event_type ENUM(
        'PAYMENT_CAPTURED',
        'AUTHORIZATION_VOIDED',
        'PAYMENT_REFUNDED'
    ) NOT NULL,
    provider_authorization_id VARCHAR(255) NULL,
    payload_hash VARCHAR(80) NOT NULL,
    outcome ENUM(
        'APPLIED',
        'DUPLICATE',
        'ALREADY_APPLIED',
        'IGNORED_STATE_REGRESSION',
        'MANUAL_REVIEW_REQUIRED',
        'UNKNOWN_AUTHORIZATION'
    ) NOT NULL,
    provider_occurred_at TIMESTAMP(6) NULL,
    received_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_payment_webhook_provider_event
        UNIQUE (provider, provider_event_id)
);

CREATE INDEX idx_payment_webhook_authorization_received
    ON payment_webhook_events(provider_authorization_id, received_at);
CREATE INDEX idx_payment_webhook_outcome_received
    ON payment_webhook_events(outcome, received_at);
