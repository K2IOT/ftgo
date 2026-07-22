CREATE SCHEMA IF NOT EXISTS eventuate;

CREATE TABLE IF NOT EXISTS eventuate.message (
    id VARCHAR(255) PRIMARY KEY,
    destination VARCHAR(1000) NOT NULL,
    headers TEXT NOT NULL,
    payload TEXT NOT NULL,
    published SMALLINT DEFAULT 0,
    message_partition SMALLINT,
    creation_time BIGINT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @message_published_index_exists = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = 'eventuate'
      AND table_name = 'message'
      AND index_name = 'message_published_idx'
);
SET @create_message_published_index = IF(
    @message_published_index_exists = 0,
    'CREATE INDEX message_published_idx ON eventuate.message(published, id)',
    'SELECT 1'
);
PREPARE create_message_published_index_stmt FROM @create_message_published_index;
EXECUTE create_message_published_index_stmt;
DEALLOCATE PREPARE create_message_published_index_stmt;

CREATE TABLE IF NOT EXISTS eventuate.received_messages (
    consumer_id VARCHAR(255) NOT NULL,
    message_id VARCHAR(255) NOT NULL,
    creation_time BIGINT,
    PRIMARY KEY (consumer_id, message_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS eventuate.offset_store (
    client_name VARCHAR(255) NOT NULL PRIMARY KEY,
    serialized_offset VARCHAR(255)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
