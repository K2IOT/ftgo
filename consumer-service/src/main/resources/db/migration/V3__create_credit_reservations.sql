ALTER TABLE consumers
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE credit_reservations (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    consumer_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    amount DECIMAL(19,2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    UNIQUE KEY uq_credit_reservation_order (order_id),
    INDEX idx_credit_reservation_consumer_status (consumer_id, status),
    CONSTRAINT fk_credit_reservation_consumer
        FOREIGN KEY (consumer_id) REFERENCES consumers(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
