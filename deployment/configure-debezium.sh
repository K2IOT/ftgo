#!/bin/bash

# Wait for Debezium Connect to be ready
echo "Waiting for Debezium Connect to be ready..."
until curl -s http://localhost:8083/ > /dev/null; do
  sleep 5
done
echo "Debezium Connect is ready"

# Function to create Debezium connector
create_connector() {
  local service_name=$1
  local database_name=$2
  local server_id=$3
  local port=$4
  
  echo "Creating Debezium connector for $service_name..."
  
  curl -X POST http://localhost:8083/connectors \
    -H "Content-Type: application/json" \
    -d '{
      "name": "'"$service_name"'-connector",
      "config": {
        "connector.class": "io.debezium.connector.mysql.MySqlConnector",
        "tasks.max": "1",
        "database.hostname": "mysql-'"$service_name"'",
        "database.port": "3306",
        "database.user": "ftgo_user",
        "database.password": "ftgo_password",
        "database.server.id": "'"$server_id"'",
        "database.server.name": "ftgo-'"$service_name"'",
        "database.include.list": "'"$database_name"'",
        "table.include.list": "'"$database_name"'.outbox",
        "database.history.kafka.bootstrap.servers": "kafka-1:9092,kafka-2:9093,kafka-3:9094",
        "database.history.kafka.topic": "schema-changes.'"$database_name"'",
        "include.schema.changes": "false",
        "transforms": "outbox",
        "transforms.outbox.type": "io.debezium.transforms.outbox.EventRouter",
        "transforms.outbox.table.field.event.id": "id",
        "transforms.outbox.table.field.event.key": "aggregate_id",
        "transforms.outbox.table.field.event.type": "event_type",
        "transforms.outbox.table.field.event.payload": "payload",
        "transforms.outbox.route.topic.replacement": "${routedByValue}",
        "transforms.outbox.table.fields.additional.placement": "destination:envelope:destination"
      }
    }'
  
  echo ""
  echo "Connector for $service_name created"
}

# Create connectors for all services
create_connector "order" "ftgo_order" "101" "3306"
create_connector "consumer" "ftgo_consumer" "102" "3307"
create_connector "restaurant" "ftgo_restaurant" "103" "3308"
create_connector "kitchen" "ftgo_kitchen" "104" "3309"
create_connector "accounting" "ftgo_accounting" "105" "3310"
create_connector "delivery" "ftgo_delivery" "106" "3311"

echo ""
echo "All Debezium connectors configured successfully"
echo "You can check connector status at: http://localhost:8083/connectors"
