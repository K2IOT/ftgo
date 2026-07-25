CREATE KEYSPACE IF NOT EXISTS ftgo_order_history_test
WITH replication = {
  'class': 'SimpleStrategy',
  'replication_factor': 1
};

USE ftgo_order_history_test;

CREATE TYPE IF NOT EXISTS line_item (
  menuitemid bigint,
  name text,
  price decimal,
  quantity int
);

CREATE TABLE IF NOT EXISTS order_history (
  orderid text PRIMARY KEY,
  consumerid bigint,
  restaurantid bigint,
  status text,
  ordertotal decimal,
  lineitems list<frozen<line_item>>,
  deliveryaddress text,
  deliverytime timestamp,
  deliverystatus text,
  ticketstatus text,
  authorizationstatus text,
  creationdate timestamp,
  updatedat timestamp,
  keywords set<text>
);

CREATE TABLE IF NOT EXISTS processed_messages (
  messageid text PRIMARY KEY,
  consumedat timestamp
);

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

CREATE TABLE IF NOT EXISTS order_history_by_consumer_v2 (
  consumer_id bigint,
  creation_month text,
  creation_date timestamp,
  order_id text,
  restaurant_id bigint,
  status text,
  order_total decimal,
  line_items list<frozen<line_item>>,
  delivery_address text,
  delivery_time timestamp,
  delivery_status text,
  ticket_status text,
  authorization_status text,
  updated_at timestamp,
  keywords set<text>,
  PRIMARY KEY ((consumer_id, creation_month), creation_date, order_id)
) WITH CLUSTERING ORDER BY (creation_date DESC, order_id ASC);

CREATE TABLE IF NOT EXISTS order_history_by_consumer_status_v2 (
  consumer_id bigint,
  status text,
  creation_month text,
  creation_date timestamp,
  order_id text,
  restaurant_id bigint,
  order_total decimal,
  line_items list<frozen<line_item>>,
  delivery_address text,
  delivery_time timestamp,
  delivery_status text,
  ticket_status text,
  authorization_status text,
  updated_at timestamp,
  keywords set<text>,
  PRIMARY KEY ((consumer_id, status, creation_month), creation_date, order_id)
) WITH CLUSTERING ORDER BY (creation_date DESC, order_id ASC);

CREATE TABLE IF NOT EXISTS order_history_by_consumer_restaurant_v2 (
  consumer_id bigint,
  restaurant_id bigint,
  creation_month text,
  creation_date timestamp,
  order_id text,
  status text,
  order_total decimal,
  line_items list<frozen<line_item>>,
  delivery_address text,
  delivery_time timestamp,
  delivery_status text,
  ticket_status text,
  authorization_status text,
  updated_at timestamp,
  keywords set<text>,
  PRIMARY KEY ((consumer_id, restaurant_id, creation_month), creation_date, order_id)
) WITH CLUSTERING ORDER BY (creation_date DESC, order_id ASC);
