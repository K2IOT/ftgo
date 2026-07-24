CREATE TABLE processed_commands (
    consumer_name VARCHAR(100) NOT NULL,
    command_id VARCHAR(255) NOT NULL,
    outcome VARCHAR(20) NOT NULL,
    reply_type VARCHAR(500) NULL,
    reply_payload JSON NULL,
    processed_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (consumer_name, command_id)
);
