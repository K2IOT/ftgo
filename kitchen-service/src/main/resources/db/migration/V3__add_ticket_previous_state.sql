ALTER TABLE tickets
    ADD COLUMN previous_state VARCHAR(50) NULL AFTER state;
