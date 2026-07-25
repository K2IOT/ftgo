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
