-- Delivery Service Database Schema
-- Creates tables for Delivery aggregate, Courier entity, Transactional Outbox, and Processed Messages

-- Deliveries table
CREATE TABLE deliveries (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id BIGINT NOT NULL UNIQUE,
    courier_id BIGINT,
    pickup_street VARCHAR(255) NOT NULL,
    pickup_city VARCHAR(100) NOT NULL,
    pickup_state VARCHAR(50) NOT NULL,
    pickup_zip_code VARCHAR(20) NOT NULL,
    delivery_street VARCHAR(255) NOT NULL,
    delivery_city VARCHAR(100) NOT NULL,
    delivery_state VARCHAR(50) NOT NULL,
    delivery_zip_code VARCHAR(20) NOT NULL,
    scheduled_time TIMESTAMP NOT NULL,
    pickup_time TIMESTAMP NULL,
    delivery_time TIMESTAMP NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_order_id (order_id),
    INDEX idx_courier_id (courier_id),
    INDEX idx_status (status),
    INDEX idx_courier_status (courier_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Couriers table
CREATE TABLE couriers (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    phone VARCHAR(50) NOT NULL,
    available BOOLEAN NOT NULL DEFAULT TRUE,
    INDEX idx_available (available)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Transactional Outbox table for reliable event publishing
CREATE TABLE outbox (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    aggregate_type VARCHAR(255) NOT NULL,
    aggregate_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    payload JSON NOT NULL,
    destination VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    INDEX idx_published (published, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Processed messages table for idempotent message processing
CREATE TABLE processed_messages (
    message_id VARCHAR(255) PRIMARY KEY,
    consumed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
