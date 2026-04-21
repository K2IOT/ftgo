#!/bin/bash

# Wait for Vault to be ready
echo "Waiting for Vault to be ready..."
until curl -s http://localhost:8200/v1/sys/health > /dev/null; do
  sleep 2
done
echo "Vault is ready"

export VAULT_ADDR='http://localhost:8200'
export VAULT_TOKEN='ftgo-root-token'

# Enable KV secrets engine
echo "Enabling KV secrets engine..."
vault secrets enable -path=secret kv-v2 2>/dev/null || echo "KV engine already enabled"

# Store database passwords
echo "Storing database secrets..."
vault kv put secret/order-service \
  db.password=ftgo_password \
  db.username=ftgo_user

vault kv put secret/consumer-service \
  db.password=ftgo_password \
  db.username=ftgo_user

vault kv put secret/restaurant-service \
  db.password=ftgo_password \
  db.username=ftgo_user

vault kv put secret/kitchen-service \
  db.password=ftgo_password \
  db.username=ftgo_user

vault kv put secret/accounting-service \
  db.password=ftgo_password \
  db.username=ftgo_user

vault kv put secret/delivery-service \
  db.password=ftgo_password \
  db.username=ftgo_user

# Create service-specific tokens
echo "Creating service tokens..."
vault token create -policy=default -display-name=order-service -ttl=720h > /tmp/order-service-token.txt
vault token create -policy=default -display-name=consumer-service -ttl=720h > /tmp/consumer-service-token.txt
vault token create -policy=default -display-name=restaurant-service -ttl=720h > /tmp/restaurant-service-token.txt
vault token create -policy=default -display-name=kitchen-service -ttl=720h > /tmp/kitchen-service-token.txt
vault token create -policy=default -display-name=accounting-service -ttl=720h > /tmp/accounting-service-token.txt
vault token create -policy=default -display-name=delivery-service -ttl=720h > /tmp/delivery-service-token.txt

echo ""
echo "Vault initialized successfully"
echo "Root token: ftgo-root-token"
echo "Service tokens saved in /tmp/*-token.txt"
echo ""
echo "To retrieve a secret:"
echo "  vault kv get secret/order-service"
