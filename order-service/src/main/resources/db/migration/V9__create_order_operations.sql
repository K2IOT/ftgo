CREATE TABLE order_operations (
  id BIGINT NOT NULL AUTO_INCREMENT,
  order_id BIGINT NOT NULL,
  operation_type VARCHAR(30) NOT NULL,
  status VARCHAR(30) NOT NULL,
  classification VARCHAR(30) NULL,
  requested_action VARCHAR(30) NULL,
  idempotency_key VARCHAR(255) NOT NULL,
  reason VARCHAR(500) NOT NULL,
  details VARCHAR(1000) NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uq_order_operations_idempotency_key (idempotency_key),
  KEY idx_order_operations_order_created (order_id, created_at),
  CONSTRAINT fk_order_operations_order
    FOREIGN KEY (order_id) REFERENCES orders(id)
) ENGINE=InnoDB;
