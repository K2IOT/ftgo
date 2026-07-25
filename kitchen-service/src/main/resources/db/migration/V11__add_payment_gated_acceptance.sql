-- Phase 02B Task 3: keep restaurant acceptance pending until payment capture succeeds.
ALTER TABLE tickets
    ADD COLUMN acceptance_request_id VARCHAR(100) NULL AFTER acceptance_deadline,
    ADD COLUMN acceptance_requested_at TIMESTAMP(6) NULL AFTER acceptance_request_id,
    ADD COLUMN capture_request_id VARCHAR(100) NULL AFTER acceptance_requested_at,
    ADD COLUMN acceptance_failure_reason VARCHAR(255) NULL AFTER capture_request_id;

CREATE UNIQUE INDEX uq_ticket_acceptance_request
    ON tickets(acceptance_request_id);
CREATE UNIQUE INDEX uq_ticket_capture_request
    ON tickets(capture_request_id);
CREATE INDEX idx_ticket_state_acceptance_requested
    ON tickets(state, acceptance_requested_at);
