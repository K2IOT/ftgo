-- Phase 02B Task 4: durable payment capture state and operation identity.
ALTER TABLE orders
    ADD COLUMN payment_state ENUM(
        'AUTHORIZED',
        'CAPTURE_PENDING',
        'CAPTURED',
        'VOIDED',
        'REFUND_PENDING',
        'REFUNDED',
        'FAILED',
        'MANUAL_REVIEW'
    ) NULL AFTER acceptance_deadline,
    ADD COLUMN acceptance_request_id VARCHAR(100) NULL AFTER payment_state,
    ADD COLUMN payment_operation_request_id VARCHAR(100) NULL AFTER acceptance_request_id,
    ADD COLUMN capture_id BIGINT NULL AFTER payment_operation_request_id,
    ADD COLUMN payment_failure_code VARCHAR(100) NULL AFTER capture_id;

-- Phase 02 orders only authorized funds; capture did not exist before this migration.
-- Preserve that fact so cancellation voids legacy authorizations instead of attempting
-- a refund with a capture reference that can never exist. Already-cancelled legacy
-- orders are terminal and therefore safely represented as VOIDED.
UPDATE orders
SET payment_state = CASE
    WHEN state = 'CANCELLED' THEN 'VOIDED'
    WHEN state IN (
        'APPROVED',
        'CANCEL_PENDING',
        'REVISION_PENDING',
        'AWAITING_RESTAURANT_ACCEPTANCE',
        'CONFIRMATION_PENDING',
        'REJECTION_PENDING'
    ) THEN 'AUTHORIZED'
    ELSE NULL
END
WHERE payment_state IS NULL;

CREATE UNIQUE INDEX uq_order_acceptance_request
    ON orders(acceptance_request_id);
CREATE UNIQUE INDEX uq_order_payment_operation_request
    ON orders(payment_operation_request_id);
CREATE INDEX idx_order_payment_state_updated
    ON orders(payment_state, updated_at);
