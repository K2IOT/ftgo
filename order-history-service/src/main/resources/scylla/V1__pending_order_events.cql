CREATE TABLE IF NOT EXISTS pending_order_events (
  order_id text,
  aggregate_version bigint,
  occurred_at timestamp,
  event_id uuid,
  event_type text,
  envelope text,
  retry_count int,
  first_seen_at timestamp,
  next_attempt_at timestamp,
  last_error text,
  PRIMARY KEY ((order_id), aggregate_version, occurred_at, event_id)
) WITH CLUSTERING ORDER BY (
  aggregate_version ASC,
  occurred_at ASC,
  event_id ASC
);
