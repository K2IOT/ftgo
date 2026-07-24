ALTER TABLE tickets
    ADD COLUMN acceptance_deadline TIMESTAMP(6) NULL,
    ADD COLUMN decision_event_id VARCHAR(36) NULL,
    ADD COLUMN decision_reason VARCHAR(100) NULL,
    ADD COLUMN decision_at TIMESTAMP(6) NULL,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

CREATE UNIQUE INDEX uq_ticket_decision_event
    ON tickets(decision_event_id);

CREATE INDEX idx_ticket_acceptance_timeout
    ON tickets(state, acceptance_deadline, id);
