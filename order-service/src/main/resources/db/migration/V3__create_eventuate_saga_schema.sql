CREATE SCHEMA IF NOT EXISTS eventuate;

CREATE TABLE IF NOT EXISTS eventuate.saga_instance_participants (
    saga_type VARCHAR(255) NOT NULL,
    saga_id VARCHAR(100) NOT NULL,
    destination VARCHAR(100) NOT NULL,
    resource VARCHAR(100) NOT NULL,
    PRIMARY KEY (saga_type, saga_id, destination, resource)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS eventuate.saga_instance (
    saga_type VARCHAR(255) NOT NULL,
    saga_id VARCHAR(100) NOT NULL,
    state_name VARCHAR(100) NOT NULL,
    last_request_id VARCHAR(100),
    end_state TINYINT(1),
    compensating TINYINT(1),
    failed TINYINT(1),
    saga_data_type VARCHAR(1000) NOT NULL,
    saga_data_json TEXT NOT NULL,
    PRIMARY KEY (saga_type, saga_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS eventuate.saga_lock_table (
    target VARCHAR(100) PRIMARY KEY,
    saga_type VARCHAR(255) NOT NULL,
    saga_id VARCHAR(100) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS eventuate.saga_stash_table (
    message_id VARCHAR(100) PRIMARY KEY,
    target VARCHAR(100) NOT NULL,
    saga_type VARCHAR(255) NOT NULL,
    saga_id VARCHAR(100) NOT NULL,
    message_headers TEXT NOT NULL,
    message_payload TEXT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
