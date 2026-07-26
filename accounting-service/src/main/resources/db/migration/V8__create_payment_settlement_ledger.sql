CREATE TABLE payment_ledger_entries (
    id BIGINT NOT NULL AUTO_INCREMENT,
    account_id BIGINT NOT NULL,
    order_id BIGINT NULL,
    authorization_id BIGINT NULL,
    operation_type VARCHAR(32) NOT NULL,
    request_id VARCHAR(191) NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'VND',
    provider_reference VARCHAR(191) NULL,
    occurred_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_payment_ledger_request UNIQUE (request_id),
    INDEX idx_payment_ledger_order (order_id, occurred_at),
    INDEX idx_payment_ledger_authorization (authorization_id, occurred_at),
    CONSTRAINT fk_payment_ledger_account FOREIGN KEY (account_id) REFERENCES accounts(id)
);
