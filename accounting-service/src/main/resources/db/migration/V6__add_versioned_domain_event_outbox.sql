-- Phase 03 Task 1: optimistic Account version and stable outbox identity.
ALTER TABLE accounts
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 AFTER consumer_id;

ALTER TABLE outbox
    ADD COLUMN event_id VARCHAR(36) NULL AFTER id,
    ADD COLUMN schema_version INT NOT NULL DEFAULT 1 AFTER event_id,
    ADD COLUMN aggregate_version BIGINT NOT NULL DEFAULT 0 AFTER schema_version;

UPDATE outbox
SET event_id = LOWER(CONCAT(
    SUBSTRING(MD5(CONCAT('accounting-outbox-', id)), 1, 8), '-',
    SUBSTRING(MD5(CONCAT('accounting-outbox-', id)), 9, 4), '-',
    SUBSTRING(MD5(CONCAT('accounting-outbox-', id)), 13, 4), '-',
    SUBSTRING(MD5(CONCAT('accounting-outbox-', id)), 17, 4), '-',
    SUBSTRING(MD5(CONCAT('accounting-outbox-', id)), 21, 12)
))
WHERE event_id IS NULL;

CREATE UNIQUE INDEX uq_outbox_event_id ON outbox(event_id);
