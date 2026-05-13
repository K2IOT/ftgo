-- Create orders table
CREATE TABLE orders (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    version INT NOT NULL DEFAULT 0,
    state VARCHAR(50) NOT NULL,
    consumer_id BIGINT NOT NULL,
    restaurant_id BIGINT NOT NULL,
    delivery_address VARCHAR(500) NOT NULL,
    delivery_time TIMESTAMP NOT NULL,
    payment_token VARCHAR(255) NOT NULL,
    order_total DECIMAL(10,2) NOT NULL,
    ticket_id BIGINT,
    authorization_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_consumer_id (consumer_id),
    INDEX idx_restaurant_id (restaurant_id),
    INDEX idx_state (state),
    INDEX idx_created_at (created_at),
    CONSTRAINT chk_state_valid CHECK (state IN ('APPROVAL_PENDING', 'APPROVED', 'REJECTED', 'CANCEL_PENDING', 'CANCELLED', 'REVISION_PENDING')),
    CONSTRAINT chk_order_total_positive CHECK (order_total > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Create order_line_items table
CREATE TABLE order_line_items (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    menu_item_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    quantity INT NOT NULL,
    FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
    INDEX idx_order_id (order_id),
    INDEX idx_menu_item_id (menu_item_id),
    CONSTRAINT chk_price_positive CHECK (price > 0),
    CONSTRAINT chk_quantity_positive CHECK (quantity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Create outbox table for Transactional Outbox pattern
-- Debezium CDC will monitor this table and publish events to Kafka
CREATE TABLE outbox (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    aggregate_type VARCHAR(255) NOT NULL,
    aggregate_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    payload JSON NOT NULL,
    destination VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    INDEX idx_published (published, created_at),
    INDEX idx_aggregate (aggregate_type, aggregate_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Create processed_messages table for idempotent event processing
-- Prevents duplicate processing of the same event
CREATE TABLE processed_messages (
    message_id VARCHAR(255) PRIMARY KEY,
    consumed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_consumed_at (consumed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Create saga_instance table for Eventuate Tram Sagas
-- Stores saga state and execution progress
CREATE TABLE saga_instance (
    saga_type VARCHAR(255) NOT NULL,
    saga_id VARCHAR(255) PRIMARY KEY,
    state_name VARCHAR(255) NOT NULL,
    last_request_id VARCHAR(255),
    saga_data_type VARCHAR(1000) NOT NULL,
    saga_data_json TEXT NOT NULL,
    INDEX idx_saga_type (saga_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Create saga_instance_participants table for Eventuate Tram Sagas
-- Tracks saga participants and their state
CREATE TABLE saga_instance_participants (
    saga_type VARCHAR(100) NOT NULL,
    saga_id VARCHAR(100) NOT NULL,
    destination VARCHAR(100) NOT NULL,
    resource VARCHAR(100) NOT NULL,
    PRIMARY KEY (saga_type, saga_id, destination, resource)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Create saga_lock_table for Eventuate Tram Sagas
-- Provides distributed locking for saga execution
CREATE TABLE saga_lock_table (
    target VARCHAR(255) PRIMARY KEY,
    saga_type VARCHAR(255) NOT NULL,
    saga_id VARCHAR(255) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Create saga_stash_table for Eventuate Tram Sagas
-- Stores stashed messages during saga execution
CREATE TABLE saga_stash_table (
    message_id VARCHAR(255) PRIMARY KEY,
    target VARCHAR(255) NOT NULL,
    saga_type VARCHAR(255) NOT NULL,
    saga_id VARCHAR(255) NOT NULL,
    message_headers TEXT NOT NULL,
    message_payload TEXT NOT NULL,
    INDEX idx_target (target)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
