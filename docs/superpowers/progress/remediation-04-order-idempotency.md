# Remediation 04 — Order API Idempotency Progress

Base `dev` SHA: `2925b7cd2995d3413f8bb831957648b8acad4746`.

## RED checkpoint

The branch currently contains only contract tests and a focused GitHub Actions gate. Production idempotency classes, migration and controller integration are intentionally absent so CI must fail for the expected missing behavior before implementation begins.

Required behavior:

- mandatory `Idempotency-Key` for create, cancel and revise;
- durable unique claim by consumer, operation and key;
- SHA-256 request binding without bearer/payment tokens;
- byte-equivalent response replay;
- `409 IDEMPOTENCY_KEY_CONFLICT` for changed payloads;
- order mutation, saga/outbox work and idempotency completion in one transaction.
