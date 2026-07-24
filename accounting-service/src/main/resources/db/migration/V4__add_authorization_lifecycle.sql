ALTER TABLE authorizations DROP CHECK chk_status_valid;

ALTER TABLE authorizations
    ADD COLUMN order_id BIGINT NULL,
    ADD COLUMN capture_request_id VARCHAR(255) NULL,
    ADD COLUMN void_request_id VARCHAR(255) NULL,
    ADD COLUMN refund_request_id VARCHAR(255) NULL,
    ADD COLUMN void_reason VARCHAR(255) NULL,
    ADD COLUMN refund_reason VARCHAR(255) NULL,
    ADD COLUMN captured_at TIMESTAMP(6) NULL,
    ADD COLUMN voided_at TIMESTAMP(6) NULL,
    ADD COLUMN refunded_at TIMESTAMP(6) NULL,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

CREATE UNIQUE INDEX uq_authorization_capture_request
    ON authorizations(capture_request_id);
CREATE UNIQUE INDEX uq_authorization_void_request
    ON authorizations(void_request_id);
CREATE UNIQUE INDEX uq_authorization_refund_request
    ON authorizations(refund_request_id);
CREATE INDEX idx_authorization_order ON authorizations(order_id);
