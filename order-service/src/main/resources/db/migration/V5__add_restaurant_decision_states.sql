ALTER TABLE orders DROP CHECK chk_state_valid;

ALTER TABLE orders
    ADD COLUMN credit_reservation_id BIGINT NULL,
    ADD COLUMN acceptance_deadline TIMESTAMP(6) NULL,
    ADD COLUMN rejection_code VARCHAR(100) NULL,
    ADD COLUMN rejection_message VARCHAR(500) NULL,
    ADD CONSTRAINT chk_state_valid CHECK (
        state IN (
            'APPROVAL_PENDING',
            'AWAITING_RESTAURANT_ACCEPTANCE',
            'CONFIRMATION_PENDING',
            'REJECTION_PENDING',
            'APPROVED',
            'REJECTED',
            'CANCEL_PENDING',
            'CANCELLED',
            'REVISION_PENDING'
        )
    );

CREATE INDEX idx_order_restaurant_decision
    ON orders(state, acceptance_deadline, id);
