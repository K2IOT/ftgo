CREATE SCHEMA IF NOT EXISTS eventuate;

CREATE TABLE IF NOT EXISTS eventuate.message (
  id VARCHAR(255) PRIMARY KEY,
  destination TEXT NOT NULL,
  headers TEXT NOT NULL,
  payload TEXT NOT NULL,
  published SMALLINT DEFAULT 0,
  message_partition SMALLINT,
  creation_time BIGINT
);

CREATE TABLE IF NOT EXISTS eventuate.received_messages (
  consumer_id VARCHAR(255),
  message_id VARCHAR(255),
  creation_time BIGINT,
  PRIMARY KEY(consumer_id, message_id)
);

CREATE TABLE IF NOT EXISTS eventuate.saga_instance(
  saga_type VARCHAR(255) NOT NULL,
  saga_id VARCHAR(100) PRIMARY KEY,
  state_name VARCHAR(100) NOT NULL,
  last_request_id VARCHAR(100),
  end_state BOOLEAN,
  compensating BOOLEAN,
  failed BOOLEAN,
  saga_data_type VARCHAR(1000) NOT NULL,
  saga_data_json VARCHAR(1000) NOT NULL
);

CREATE TABLE IF NOT EXISTS eventuate.saga_instance_participants (
  saga_type VARCHAR(255) NOT NULL,
  saga_id VARCHAR(100) NOT NULL,
  destination VARCHAR(100) NOT NULL,
  resource VARCHAR(100) NOT NULL,
  PRIMARY KEY(saga_id, destination, resource)
);
