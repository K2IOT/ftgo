CREATE KEYSPACE IF NOT EXISTS ftgo_order_history_test
WITH replication = {
  'class': 'SimpleStrategy',
  'replication_factor': 1
};

CREATE TYPE IF NOT EXISTS ftgo_order_history_test.line_item (
  menuitemid bigint,
  name text,
  price decimal,
  quantity int
);

CREATE TABLE IF NOT EXISTS ftgo_order_history_test.order_history (
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

CREATE TABLE IF NOT EXISTS ftgo_order_history_test.processed_messages (
  messageid text PRIMARY KEY,
  consumedat timestamp
);
