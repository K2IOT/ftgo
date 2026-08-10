CREATE INDEX idx_outbox_created_at ON outbox (created_at);
CREATE INDEX idx_processed_commands_outcome_processed_at ON processed_commands (outcome, processed_at);
