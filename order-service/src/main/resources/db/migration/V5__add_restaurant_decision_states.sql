ALTER TABLE orders
    ADD COLUMN credit_reservation_id BIGINT NULL,
    ADD COLUMN acceptance_deadline TIMESTAMP(6) NULL,
    ADD COLUMN rejection_code VARCHAR(100) NULL,
    ADD COLUMN rejection_message VARCHAR(500) NULL;

CREATE INDEX idx_order_restaurant_decision
    ON orders(state, acceptance_deadline, id);
