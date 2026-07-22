ALTER TABLE saga_instance
    ADD COLUMN end_state TINYINT(1) NULL AFTER last_request_id,
    ADD COLUMN compensating TINYINT(1) NULL AFTER end_state,
    ADD COLUMN failed TINYINT(1) NULL AFTER compensating,
    DROP PRIMARY KEY,
    ADD PRIMARY KEY (saga_type, saga_id);
