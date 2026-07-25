-- Phase 03 Task 1: stable event identity and version metadata.
-- event_id remains nullable during the rolling-upgrade window so the previous
-- producer can continue writing until all instances have been replaced.
ALTER TABLE outbox
    ADD COLUMN event_id VARCHAR(36) NULL AFTER id,
    ADD COLUMN schema_version INT NOT NULL DEFAULT 1 AFTER event_id,
    ADD COLUMN aggregate_version BIGINT NOT NULL DEFAULT 0 AFTER schema_version;

-- Existing rows receive a deterministic UUID-shaped identity derived from the
-- service boundary and immutable outbox row ID. Re-running the expression for
-- the same row yields the same value.
UPDATE outbox
SET event_id = LOWER(CONCAT(
    SUBSTRING(MD5(CONCAT('order-outbox-', id)), 1, 8), '-',
    SUBSTRING(MD5(CONCAT('order-outbox-', id)), 9, 4), '-',
    SUBSTRING(MD5(CONCAT('order-outbox-', id)), 13, 4), '-',
    SUBSTRING(MD5(CONCAT('order-outbox-', id)), 17, 4), '-',
    SUBSTRING(MD5(CONCAT('order-outbox-', id)), 21, 12)
))
WHERE event_id IS NULL;

CREATE UNIQUE INDEX uq_outbox_event_id ON outbox(event_id);
