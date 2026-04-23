-- ScyllaDB Schema for Order History Service (CQRS Read Model)
-- This schema supports high-performance queries for order history

-- Create keyspace if not exists (for local development)
-- In production, keyspace should be created separately with appropriate replication
CREATE KEYSPACE IF NOT EXISTS ftgo_order_history
    WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 3}
    AND durable_writes = true;

USE ftgo_order_history;

-- User-defined type for line items
CREATE TYPE IF NOT EXISTS line_item (
    menu_item_id BIGINT,
    name TEXT,
    price DECIMAL,
    quantity INT
);

-- Primary table: order_history
-- Partition key: order_id (supports findOrder(orderId) queries)
CREATE TABLE IF NOT EXISTS order_history (
    order_id TEXT PRIMARY KEY,
    consumer_id BIGINT,
    restaurant_id BIGINT,
    status TEXT,
    order_total DECIMAL,
    line_items LIST<FROZEN<line_item>>,
    delivery_address TEXT,
    delivery_time TIMESTAMP,
    delivery_status TEXT,
    ticket_status TEXT,
    authorization_status TEXT,
    creation_date TIMESTAMP,
    updated_at TIMESTAMP,
    keywords SET<TEXT>
);

-- Materialized view for querying by consumer
-- Partition key: consumer_id, Clustering key: creation_date DESC
-- Supports findOrderHistory(consumerId) with date range filtering
CREATE MATERIALIZED VIEW IF NOT EXISTS order_history_by_consumer AS
    SELECT *
    FROM order_history
    WHERE consumer_id IS NOT NULL
      AND creation_date IS NOT NULL
      AND order_id IS NOT NULL
    PRIMARY KEY (consumer_id, creation_date, order_id)
    WITH CLUSTERING ORDER BY (creation_date DESC);

-- Table for tracking processed messages (idempotency)
-- Prevents duplicate event processing with at-least-once Kafka delivery
CREATE TABLE IF NOT EXISTS processed_messages (
    message_id TEXT PRIMARY KEY,
    consumed_at TIMESTAMP
);

-- Indexes for additional query patterns (optional, use sparingly in ScyllaDB)
-- ScyllaDB performs best with partition key queries
-- CREATE INDEX IF NOT EXISTS idx_order_status ON order_history (status);
-- CREATE INDEX IF NOT EXISTS idx_restaurant_id ON order_history (restaurant_id);
