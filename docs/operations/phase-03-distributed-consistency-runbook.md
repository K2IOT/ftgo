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

## Messaging Retention Lifecycle

Messaging retention is available for the `outbox` table in Order, Consumer, Restaurant, Kitchen, Accounting and Delivery, and for `processed_commands` in Consumer, Kitchen and Accounting. Cleanup is intentionally disabled by default.

Defaults:

```text
FTGO_MESSAGE_RETENTION_ENABLED=false
FTGO_MESSAGE_RETENTION_OUTBOX_RETENTION=P30D
FTGO_MESSAGE_RETENTION_COMPLETED_COMMAND_RETENTION=P30D
FTGO_MESSAGE_RETENTION_BATCH_SIZE=500
FTGO_MESSAGE_RETENTION_INTERVAL=PT10M
```

Both retention periods must be at least `P7D`. Startup validation rejects shorter periods. Each scheduled run deletes at most 500 rows from each existing table. Outbox rows are eligible only when `created_at` is older than the configured cutoff. Processed commands are eligible only when `processed_at` is older than the configured cutoff and `outcome <> 'PROCESSING'`.

**Never manually delete a `processed_commands` row whose outcome is `PROCESSING`.** Active claims protect command idempotency and may still be referenced by an in-flight saga or replayed Kafka record.

### Pre-enable and dry-run checks

Keep `FTGO_MESSAGE_RETENTION_ENABLED=false` while performing these checks:

1. Confirm Kafka topic retention and the maximum supported replay/recovery window are both shorter than the configured database retention periods. Keep a safety margin; do not set database retention equal to the longest possible replay window.
2. Confirm Debezium connectors are `RUNNING`, have no sustained source-record lag, and are not recovering a backlog older than the proposed outbox cutoff.
3. Confirm Kafka consumer lag is healthy for the affected service topics. A growing or unbounded lag is a stop condition.
4. Inspect `ftgo_outbox_oldest_age_seconds{service}` and `ftgo_outbox_cleanup_eligible_count{service}`. The cleanup-eligible gauge is the outbox dry-run signal while deletion remains disabled.
5. For Consumer, Kitchen and Accounting, run a read-only count using the same completed-command predicate before enablement: `processed_at < <cutoff> AND outcome <> 'PROCESSING'`. Do not convert this inspection into a manual `DELETE`.
6. Confirm the retention indexes from the service Flyway migration exist before enabling cleanup.

### Enablement

Enable one service at a time by setting `FTGO_MESSAGE_RETENTION_ENABLED=true` and rolling/restarting that service. Leave the retention periods, batch size and interval at their defaults for the initial rollout unless a reviewed operating requirement says otherwise.

After enablement, watch:

```text
ftgo_message_retention_deleted_rows_total{service,table}
ftgo_message_retention_failures_total{service,table}
ftgo_message_retention_last_success_epoch_seconds{service,table}
ftgo_message_retention_cleanup_duration_seconds{service,table}
```

Expected behavior is bounded progress: no more than the configured batch size is deleted from a table in one run, and repeated runs gradually drain eligible rows. Metric labels are limited to service and table; event IDs and payload values must never be labels.

Before enabling the next service, confirm that connector lag and Kafka consumer lag remain healthy, cleanup failures are zero or understood, and database latency has not materially regressed.

### Retention rollback

If cleanup causes unexpected load, retention-policy uncertainty, connector lag, or elevated failures:

1. Set `FTGO_MESSAGE_RETENTION_ENABLED=false` for the affected service and roll/restart it so the scheduled worker is no longer created.
2. Keep the additive retention indexes in place; they are safe to retain and are not a rollback hazard.
3. Re-check Debezium connector health, Kafka lag and the replay window before considering re-enable.
4. Preserve all remaining outbox and processed-command rows for diagnosis. Do not manually delete active command claims.
5. If already-deleted rows were still required for replay, stop further replay attempts and follow the incident recovery process; disabling cleanup prevents additional deletion but cannot restore deleted data.

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
10. Apply message-retention indexes and deploy retention-capable binaries with cleanup disabled.
11. Complete the messaging retention pre-enable checks before enabling cleanup one service at a time.

## Verification

Targeted commands:

```bash
./gradlew :common:test --tests '*DomainEventEnvelopeTest' --tests '*IdempotentCommandExecutorTest'
./gradlew :delivery-service:test --tests '*EventIdentityTest' --tests '*DeadLetterPublishingTest'
./gradlew :order-history-service:test --tests '*EventIdentityTest' --tests '*DeadLetterPublishingTest' --tests '*OutOfOrderEventIntegrationTest' --tests '*OrderHistoryPagingIntegrationTest'
./gradlew :order-service:test --tests '*OrderOperationReconcilerTest'
./gradlew :common:test --tests '*JdbcMessageRetentionWorkerTest'
./gradlew :order-service:test :consumer-service:test :restaurant-service:test :kitchen-service:test :accounting-service:test :delivery-service:test --tests '*MigrationTest'
```

Live distributed-failure validation:

```bash
bash scripts/smoke/verify-distributed-consistency.sh --runs 2
```

Both clean-state cycles must pass with identical outcomes.

## Rollback

1. Disable messaging retention first if it is enabled for any service.
2. Stop new envelope producers before reverting Debezium identity mapping.
3. Keep additive columns, tables and retention indexes; do not drop them during rollback.
4. Restore the previous connector configuration only after confirming no new envelope-only consumers depend on the event ID header.
5. Disable scheduled pending reconciliation if it is causing load, but preserve `pending_order_events`.
6. Disable operator repair routes at the network layer before disabling the monitor.
7. Roll back application binaries in reverse deployment order.
8. Preserve DLT, processed-command, pending-event and order-operation records for diagnosis.

Do not roll back by deleting idempotency, active command claims or pending rows. That can convert a recoverable incident into duplicate financial or fulfillment actions.
