# Phase 03 Distributed Consistency Runbook

## Scope

Phase 03 protects saga commands, domain events and CQRS projections against:

- duplicate command delivery and lost replies;
- duplicate or same-type domain events for one aggregate;
- out-of-order projection events;
- poison messages and unbounded retry;
- process restart after a local transaction commits;
- stuck saga semantic locks;
- Debezium pause, restart and delayed outbox delivery.

Security integration and endpoint authorization remain Phase 04 responsibilities. Until Phase 04 is deployed, the saga operations endpoint must only be reachable from the protected operator network.

## Event Contract

Every new domain event is persisted as a versioned envelope containing:

- UUID `eventId`;
- `eventType` and `schemaVersion`;
- aggregate type, ID and version;
- occurrence timestamp;
- correlation, causation and trace metadata;
- business payload.

The aggregate ID remains the Kafka partition key. It must never be used as the event identity.

Debezium Event Router configuration must use:

```text
transforms.outbox.table.field.event.id=event_id
transforms.outbox.table.field.event.key=aggregate_id
```

During the rolling-upgrade window, consumers accept both legacy payloads and the versioned envelope. New producers must only write the envelope.

## Command Replay

Consumer, Kitchen and Accounting participant handlers persist their reply in `processed_commands` in the same local transaction as the domain mutation.

Primary key:

```text
(consumer_name, command_id)
```

When a command is delivered again, the handler returns the original outcome, reply type and reply JSON. Operators must not delete `processed_commands` rows while a saga or its Kafka records can still be replayed.

### Lost-reply diagnosis

1. Find the command ID in the participant's `processed_commands` table.
2. Verify the business row exists exactly once.
3. Verify the stored reply type and JSON are valid.
4. Replay or allow Kafka to redeliver the original command.
5. Confirm the same business identifier is returned and no second domain row is created.

## Kafka Retry and Dead Letters

Delivery and Order History use bounded retry intervals:

```text
1 second → 5 seconds → 30 seconds → DLT
```

Permanent contract, JSON, schema and event-identity failures are non-retryable. Transient dependency failures are retryable.

DLT topic convention:

```text
<source-topic>.DLT
```

DLT metadata includes source topic/partition/offset from Spring Kafka plus FTGO event ID, correlation ID when available, and exception class. Raw payload and PII must not be used as metric labels.

Relevant metric:

```text
ftgo_kafka_dlt_total{source_topic,exception}
```

### Poison-message response

1. Locate the record in the source topic DLT.
2. Inspect event ID, correlation ID and exception class.
3. Fix the producer or payload transformation before replay.
4. Replay into the original topic using the original aggregate key and a valid event ID.
5. Confirm a later valid record on the same partition was processed.

## Out-of-Order Order History Events

When the canonical order record or another prerequisite is missing, Order History writes the complete envelope to `pending_order_events` instead of falsely marking the projection as applied.

Partition and clustering order:

```text
partition: order_id
clustering: aggregate_version ASC, occurred_at ASC, event_id ASC
```

A successful prerequisite event drains up to 100 pending records synchronously. The scheduled reconciler handles remaining due records with capped backoff.

Terminal policy:

- 20 unsuccessful attempts; or
- 24 hours since first observation.

Terminal pending records are published to:

```text
net.ftgo.orderhistory.projection.DLT
```

Relevant metric:

```text
ftgo_order_history_pending_terminal_total{event_type}
```

## Scylla Query Tables and Paging

Order History writes the canonical record plus dedicated monthly bucket tables:

- consumer;
- consumer and status;
- consumer and restaurant.

Partitioning includes `creation_month`; clustering is `creation_date DESC, order_id`.

Paging tokens contain both the current month bucket and the Cassandra driver paging state returned by the previous query. Tokens are opaque and must not be modified by clients.

Unsupported baseline queries:

- keyword filtering without an indexed search backend;
- simultaneous status and restaurant filtering.

Both return HTTP 400 with a stable error code.

## Saga Reconciliation

The stuck-saga monitor scans durable order semantic-lock states:

- `APPROVAL_PENDING`: default threshold 5 minutes;
- confirmation, rejection, cancellation and revision pending: default threshold 10 minutes.

Classifications:

- `RESUMABLE`: safe to reconstruct from durable aggregate state;
- `COMPENSATABLE`: safe to restart a durable compensation path;
- `COMPLETED`: durable order state already represents completion;
- `MANUAL_REVIEW`: financial or participant progress is ambiguous.

Revision payment state is never auto-forced to success.

Operator repair requests require:

- order ID;
- action;
- human-readable reason;
- unique idempotency key.

The same idempotency key executes at most once. Repair attempts are persisted in `order_operations` and logged without payment tokens or raw payloads.

Relevant metrics:

```text
ftgo_stuck_saga_count
ftgo_stuck_saga_detected_total{operation,classification}
ftgo_order_reconciliation_action_total{action,classification}
ftgo_order_reconciliation_failure_total{action,exception}
```

## Outbox Observability

Debezium does not update the legacy `published` flag. Phase 03 therefore treats the following gauge as an explicit row-backlog proxy rather than a delivery acknowledgement:

```text
ftgo_outbox_unpublished_count{service,semantics="row_backlog_proxy"}
```

Additional metrics:

```text
ftgo_outbox_oldest_age_seconds{service}
ftgo_outbox_cleanup_eligible_count{service}
ftgo_outbox_publish_error_total{service}
```

Kafka/connector health and consumer lag remain the delivery source of truth.

## Deployment Order

1. Apply additive SQL and Scylla schema changes.
2. Deploy consumers that can read legacy and versioned event formats.
3. Deploy participant processed-command tables and handlers.
4. Deploy envelope producers.
5. Change Debezium Event Router identity to `event_id`.
6. Provision DLT topics with matching partition counts.
7. Enable Order History pending reconciliation and query-table writes.
8. Enable saga monitor, operator API and outbox metrics.
9. Keep legacy payload compatibility for one release window.

## Verification

Targeted commands:

```bash
./gradlew :common:test --tests '*DomainEventEnvelopeTest' --tests '*IdempotentCommandExecutorTest'
./gradlew :delivery-service:test --tests '*EventIdentityTest' --tests '*DeadLetterPublishingTest'
./gradlew :order-history-service:test --tests '*EventIdentityTest' --tests '*DeadLetterPublishingTest' --tests '*OutOfOrderEventIntegrationTest' --tests '*OrderHistoryPagingIntegrationTest'
./gradlew :order-service:test --tests '*OrderOperationReconcilerTest'
```

Live distributed-failure validation:

```bash
bash scripts/smoke/verify-distributed-consistency.sh --runs 2
```

Both clean-state cycles must pass with identical outcomes.

## Rollback

1. Stop new envelope producers before reverting Debezium identity mapping.
2. Keep additive columns and tables; do not drop them during rollback.
3. Restore the previous connector configuration only after confirming no new envelope-only consumers depend on the event ID header.
4. Disable scheduled pending reconciliation if it is causing load, but preserve `pending_order_events`.
5. Disable operator repair routes at the network layer before disabling the monitor.
6. Roll back application binaries in reverse deployment order.
7. Preserve DLT, processed-command, pending-event and order-operation records for diagnosis.

Do not roll back by deleting idempotency or pending rows. That can convert a recoverable incident into duplicate financial or fulfillment actions.
