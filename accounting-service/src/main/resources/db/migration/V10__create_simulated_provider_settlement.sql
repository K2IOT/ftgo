CREATE TABLE simulated_provider_payments (
    authorization_id BIGINT NOT NULL,
    order_id BIGINT NULL,
    authorized_amount DECIMAL(19, 2) NOT NULL,
    captured_amount DECIMAL(19, 2) NOT NULL DEFAULT 0.00,
    refunded_amount DECIMAL(19, 2) NOT NULL DEFAULT 0.00,
    status VARCHAR(32) NOT NULL,
    provider_reference VARCHAR(191) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (authorization_id),
    INDEX idx_provider_payment_order (order_id),
    INDEX idx_provider_payment_status (status)
);

CREATE TABLE simulated_provider_operations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    request_id VARCHAR(191) NOT NULL,
    authorization_id BIGINT NOT NULL,
    order_id BIGINT NULL,
    operation_type VARCHAR(32) NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    provider_reference VARCHAR(191) NULL,
    reason VARCHAR(255) NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_provider_operation_request UNIQUE (request_id),
    INDEX idx_provider_operation_authorization (authorization_id, created_at),
    INDEX idx_provider_operation_outcome (outcome, updated_at)
);