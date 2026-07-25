-- Phase 02B Task 1: durable provider-backed capture and refund operations.
CREATE TABLE payment_captures (
    id BIGINT NOT NULL AUTO_INCREMENT,
    authorization_id BIGINT NOT NULL,
    request_id VARCHAR(255) NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    status ENUM('PENDING', 'SUCCEEDED', 'FAILED') NOT NULL,
    provider_capture_id VARCHAR(255) NULL,
    failure_code VARCHAR(100) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    completed_at TIMESTAMP(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_payment_capture_authorization
        FOREIGN KEY (authorization_id) REFERENCES authorizations(id),
    CONSTRAINT chk_payment_capture_amount_positive CHECK (amount > 0),
    CONSTRAINT uq_payment_capture_request UNIQUE (request_id),
    CONSTRAINT uq_payment_capture_provider UNIQUE (provider_capture_id)
);

CREATE INDEX idx_payment_capture_authorization_status
    ON payment_captures(authorization_id, status);

CREATE TABLE payment_refunds (
    id BIGINT NOT NULL AUTO_INCREMENT,
    authorization_id BIGINT NOT NULL,
    request_id VARCHAR(255) NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    reason VARCHAR(255) NOT NULL,
    status ENUM('PENDING', 'SUCCEEDED', 'FAILED') NOT NULL,
    provider_refund_id VARCHAR(255) NULL,
    failure_code VARCHAR(100) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    completed_at TIMESTAMP(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_payment_refund_authorization
        FOREIGN KEY (authorization_id) REFERENCES authorizations(id),
    CONSTRAINT chk_payment_refund_amount_positive CHECK (amount > 0),
    CONSTRAINT uq_payment_refund_request UNIQUE (request_id),
    CONSTRAINT uq_payment_refund_provider UNIQUE (provider_refund_id)
);

CREATE INDEX idx_payment_refund_authorization_status
    ON payment_refunds(authorization_id, status);
