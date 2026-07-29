CREATE TABLE api_idempotency_records (
  consumer_id BIGINT NOT NULL,
  operation VARCHAR(100) NOT NULL,
  idempotency_key VARCHAR(255) NOT NULL,
  request_hash BINARY(32) NOT NULL,
  state VARCHAR(20) NOT NULL,
  http_status INT NULL,
  response_json MEDIUMTEXT NULL,
  resource_id BIGINT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  expires_at TIMESTAMP(6) NOT NULL,
  PRIMARY KEY (consumer_id, operation, idempotency_key),
  KEY idx_api_idempotency_expiry (expires_at),
  CONSTRAINT chk_api_idempotency_state
    CHECK (state IN ('PROCESSING', 'COMPLETED'))
) ENGINE=InnoDB;
