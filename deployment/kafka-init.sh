#!/usr/bin/env bash
set -euo pipefail

bootstrap_servers="${KAFKA_BOOTSTRAP_SERVERS:-kafka-1:9092,kafka-2:9092,kafka-3:9092}"
topics=(
  net.ftgo.orderservice.domain.Order
  net.ftgo.consumerservice.domain.Consumer
  net.ftgo.restaurantservice.domain.Restaurant
  net.ftgo.kitchenservice.domain.Ticket
  net.ftgo.accountingservice.domain.Account
  net.ftgo.deliveryservice.domain.Delivery
  orderService
  consumerService
  kitchenService
  accountingService
  deliveryService
  createOrderSagaReply
  cancelOrderSagaReply
  reviseOrderSagaReply
)

for topic in "${topics[@]}"; do
  kafka-topics --create --if-not-exists \
    --bootstrap-server "${bootstrap_servers}" \
    --topic "${topic}" \
    --partitions 3 \
    --replication-factor 3
done

echo "Provisioned ${#topics[@]} Kafka topics"
