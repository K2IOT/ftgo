-- Phase 02B Task 5: durable idempotency key for cancellation settlement.
ALTER TABLE orders
    ADD COLUMN payment_settlement_request_id VARCHAR(100) NULL AFTER payment_failure_code;

CREATE UNIQUE INDEX uq_order_payment_settlement_request
    ON orders(payment_settlement_request_id);
