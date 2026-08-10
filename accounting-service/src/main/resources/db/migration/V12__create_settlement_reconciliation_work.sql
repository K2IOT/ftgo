CREATE TABLE settlement_reconciliation_work (
    authorization_id BIGINT NOT NULL,
    next_attempt_at DATETIME(6) NOT NULL,
    locked_until DATETIME(6) NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    last_error VARCHAR(1000) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (authorization_id),
    CONSTRAINT fk_settlement_reconciliation_work_authorization
        FOREIGN KEY (authorization_id) REFERENCES authorizations(id) ON DELETE CASCADE,
    INDEX idx_settlement_reconciliation_work_due
        (next_attempt_at, locked_until, authorization_id)
);

INSERT INTO settlement_reconciliation_work (authorization_id, next_attempt_at)
SELECT id, CURRENT_TIMESTAMP(6)
FROM authorizations
WHERE status <> 'DENIED';
