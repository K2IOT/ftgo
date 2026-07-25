-- Phase 02B Task 7: idempotent payment reconciliation and manual review.
CREATE TABLE payment_reconciliation_cases (
    id BIGINT NOT NULL AUTO_INCREMENT,
    case_key VARCHAR(255) NOT NULL,
    authorization_id BIGINT NULL,
    provider_authorization_id VARCHAR(255) NULL,
    provider_reference VARCHAR(255) NULL,
    case_type ENUM(
        'AMOUNT_MISMATCH',
        'UNKNOWN_PROVIDER_CHARGE',
        'PROVIDER_STATE_CONFLICT',
        'PROVIDER_AUTHORIZATION_NOT_FOUND'
    ) NOT NULL,
    severity ENUM('WARNING', 'HIGH', 'CRITICAL') NOT NULL,
    status ENUM('OPEN', 'RESOLVED') NOT NULL,
    expected_amount DECIMAL(10, 2) NULL,
    provider_amount DECIMAL(10, 2) NULL,
    summary VARCHAR(500) NOT NULL,
    occurrences INT NOT NULL DEFAULT 1,
    first_detected_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    last_detected_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_payment_reconciliation_case_key UNIQUE (case_key),
    CONSTRAINT fk_payment_reconciliation_authorization
        FOREIGN KEY (authorization_id) REFERENCES authorizations(id)
);

CREATE INDEX idx_payment_reconciliation_status_detected
    ON payment_reconciliation_cases(status, first_detected_at);
CREATE INDEX idx_payment_reconciliation_provider_authorization
    ON payment_reconciliation_cases(provider_authorization_id, status);
