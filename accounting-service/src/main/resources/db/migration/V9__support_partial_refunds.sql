ALTER TABLE authorizations
    ADD COLUMN refunded_amount DECIMAL(19, 2) NOT NULL DEFAULT 0.00 AFTER refund_reason;

ALTER TABLE authorizations
    MODIFY COLUMN status ENUM(
        'APPROVED',
        'DENIED',
        'REVERSED',
        'AUTHORIZED',
        'CAPTURED',
        'PARTIALLY_REFUNDED',
        'VOIDED',
        'REFUNDED'
    ) NOT NULL;

CREATE TABLE payment_refunds (
    id BIGINT NOT NULL AUTO_INCREMENT,
    authorization_id BIGINT NOT NULL,
    request_id VARCHAR(191) NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    reason VARCHAR(255) NULL,
    provider_reference VARCHAR(191) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_payment_refund_request UNIQUE (request_id),
    CONSTRAINT fk_payment_refund_authorization FOREIGN KEY (authorization_id) REFERENCES authorizations(id),
    INDEX idx_payment_refund_authorization (authorization_id, created_at)
);