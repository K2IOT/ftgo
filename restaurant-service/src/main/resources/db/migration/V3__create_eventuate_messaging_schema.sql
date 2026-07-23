CREATE TABLE IF NOT EXISTS message (
    id VARCHAR(255) PRIMARY KEY,
    destination VARCHAR(1000) NOT NULL,
    headers TEXT NOT NULL,
    payload TEXT NOT NULL,
    published SMALLINT DEFAULT 0,
    message_partition SMALLINT,
    creation_time BIGINT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX message_published_idx ON message(published, id);

CREATE TABLE IF NOT EXISTS received_messages (
    consumer_id VARCHAR(255) NOT NULL,
    message_id VARCHAR(255) NOT NULL,
    creation_time BIGINT,
    PRIMARY KEY (consumer_id, message_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS offset_store (
    client_name VARCHAR(255) NOT NULL PRIMARY KEY,
    serialized_offset VARCHAR(255)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
