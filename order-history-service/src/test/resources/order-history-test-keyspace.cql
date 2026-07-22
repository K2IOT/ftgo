CREATE KEYSPACE IF NOT EXISTS ftgo_order_history_test
WITH replication = {
  'class': 'SimpleStrategy',
  'replication_factor': 1
};

CREATE TYPE IF NOT EXISTS ftgo_order_history_test.line_item (
  menu_item_id bigint,
  name text,
  price decimal,
  quantity int
);

CREATE TABLE IF NOT EXISTS ftgo_order_history_test.order_history (
  order_id text PRIMARY KEY,
  consumer_id bigint,
  restaurant_id bigint,
  status text,
  order_total decimal,
  line_items list<frozen<line_item>>,
  delivery_address text,
  delivery_time timestamp,
  delivery_status text,
  ticket_status text,
  authorization_status text,
  creation_date timestamp,
  updated_at timestamp,
  keywords set<text>
);

CREATE TABLE IF NOT EXISTS ftgo_order_history_test.processed_messages (
  message_id text PRIMARY KEY,
  consumed_at timestamp
);
