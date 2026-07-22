CREATE KEYSPACE IF NOT EXISTS ftgo_order_history_test
WITH replication = {
  'class': 'SimpleStrategy',
  'replication_factor': 1
};
