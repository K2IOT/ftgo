# FTGO Payment Settlement Design Addendum

**Ngày:** 2026-07-22  
**Áp dụng cho:** FTGO Production Readiness Design và Phase 02 Business Correctness.

## 1. Quyết định settlement

Payment authorization không phải pivot. Hệ thống chỉ capture sau khi restaurant chấp nhận ticket, nhưng trước khi kitchen được phép chuyển sang `PREPARING`.

Kitchen ticket state được mở rộng:

```text
AWAITING_ACCEPTANCE
  -> ACCEPTANCE_PENDING_PAYMENT
  -> ACCEPTED
  -> PREPARING
```

`POST /tickets/{ticketId}/accept` chỉ bắt đầu acceptance process và trả operation resource; nó không lập tức cho phép chuẩn bị món.

## 2. Capture Payment Saga

Order Service là orchestrator vì sở hữu `orderId`, `authorizationId` và order payment state.

```text
TicketAcceptanceRequested event
  -> Order Service starts CapturePaymentSaga
       1. local: Order PAYMENT_CAPTURE_PENDING
       2. Accounting: CaptureAuthorizationCommand
       3. Kitchen: ConfirmTicketAcceptanceCommand
       4. local: Order payment CAPTURED, operation SUCCEEDED
```

Capture là pivot. Sau capture, confirm ticket acceptance và local completion phải idempotent/retriable.

Nếu capture bị decline hoặc lỗi non-retryable:

```text
Kitchen: UndoTicketAcceptanceCommand
Accounting: void authorization when provider state still AUTHORIZED
Order: PAYMENT_FAILED or REJECTED according to policy
Operation: FAILED/COMPENSATED
```

Baseline policy: capture decline làm order `REJECTED`, ticket trở lại/cancelled và consumer credit được release.

## 3. Cancel/refund policy

Cancel Order Saga chọn action theo durable payment state:

- `AUTHORIZED` -> void authorization.
- `CAPTURED` -> create refund.
- `VOIDED`/`REFUNDED` -> idempotent success.
- unknown/ambiguous provider state -> manual review, không tự force order success.

Refund có business key `cancel-order-{orderId}` và retry trả cùng refund ID.

## 4. Provider webhooks

Accounting Service expose internal/public-provider webhook endpoint riêng cho provider. Endpoint phải:

- verify provider signature và timestamp
- reject replay ngoài tolerance window
- persist provider event ID unique
- return idempotent success for duplicate provider event
- update local authorization/payment/refund state only through allowed transition
- emit versioned accounting domain event

Webhook không tin order/payment IDs từ query/header ngoài signed provider payload.

## 5. Reconciliation

Scheduled reconciler queries provider for local records stuck in pending/unknown state. It may safely update when provider response is authoritative and transition is monotonic. Ambiguous mismatch is written to reconciliation case table and alerts operations.

No automatic action may charge, capture or refund a second time to resolve an ambiguous state.

## 6. Financial invariants

- One successful authorization per authorization request ID.
- At most one successful capture per provider authorization.
- Sum of successful refunds cannot exceed captured amount.
- Every provider event is applied at most once.
- Every local financial mutation has provider reference, correlation ID and audit timestamp.
- Order payment state and Accounting state are eventually reconcilable by order/authorization/provider references.
