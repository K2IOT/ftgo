# FTGO Production Readiness Design

**Ngày:** 2026-07-22  
**Nhánh mục tiêu:** `dev`  
**Phạm vi:** Toàn bộ backend FTGO gồm API Gateway, Order, Consumer, Restaurant, Kitchen, Accounting, Delivery, Order History, messaging, database, deployment và release governance.

## 1. Mục tiêu

Đưa FTGO từ trạng thái proof-of-concept có đủ pattern microservice sang một hệ thống có thể vận hành production với các thuộc tính sau:

- Mọi service khởi động được từ database và volume rỗng bằng migration được kiểm chứng.
- Create, Cancel và Revise Order chạy xuyên service, retry an toàn và không tạo giao dịch trùng.
- Giá, thông tin món, consumer identity và resource ownership không thể bị client giả mạo.
- Payment được đóng gói sau một provider abstraction, không lưu raw token và có authorize, void, capture, refund, webhook và reconciliation boundary rõ ràng.
- Domain event có envelope/version/identity chuẩn; consumer xử lý duplicate, out-of-order và poison event.
- Gateway và từng service thực hiện authentication/authorization phù hợp; network policy ngăn bypass gateway.
- Deployment có immutable image, secret management, probe, resource limits, autoscaling, telemetry, backup và disaster-recovery procedure.
- Mọi thay đổi phải đi qua CI quality gate, contract test, E2E saga test và security scan.

## 2. Quyết định kiến trúc

### 2.1 Giữ nguyên service boundaries

Không merge hoặc tách thêm service trong chương trình hardening này. Các bounded context hiện tại được giữ nguyên:

- Order: nguồn sự thật của order lifecycle và saga orchestration.
- Consumer: consumer profile và credit reservation.
- Restaurant: restaurant/menu/authoritative pricing.
- Kitchen: ticket lifecycle.
- Accounting: payment authorization lifecycle.
- Delivery: courier/delivery lifecycle.
- Order History: CQRS read model.
- API Gateway: edge authentication, routing, throttling và composition.

Việc giữ boundaries giúp ưu tiên sửa correctness và operability thay vì thay đổi topology.

### 2.2 Hai đường messaging tách biệt

Hệ thống tiếp tục dùng hai cơ chế, nhưng hợp đồng phải tách rõ:

1. **Eventuate Tram** cho saga command, reply và saga persistence.
2. **Custom transactional outbox + Debezium** cho public domain events.

Eventuate dùng schema chính thức tương thích chính xác với version dependency. Custom outbox dùng một event envelope chung và Debezium Outbox Event Router được cấu hình route theo cột `destination`.

Không dùng Kafka record key làm message identity. Kafka key là aggregate ID để giữ ordering; event ID lấy từ header `id` hoặc field `eventId` trong envelope.

### 2.3 Authoritative order pricing

Public Create/Revise Order API chỉ nhận `menuItemId` và `quantity`. Order Service gọi Restaurant Service để lấy snapshot authoritative gồm:

- restaurant ID
- menu item ID
- menu version
- name
- unit price
- currency
- availability

Order lưu snapshot này trong line item. Client không được truyền name hoặc price. Giai đoạn đầu dùng synchronous REST client có timeout/circuit breaker; local menu projection chỉ được cân nhắc sau khi correctness đã ổn định.

### 2.4 Async operation resource

Create, Cancel và Revise là operation bất đồng bộ. API trả `202 Accepted` cùng `operationId` và `Location`. Order Service lưu `OrderOperation` trong cùng local transaction với semantic lock/saga start state. Client theo dõi bằng `GET /operations/{operationId}`.

Các trạng thái chuẩn:

- `PENDING`
- `SUCCEEDED`
- `FAILED`
- `COMPENSATING`
- `COMPENSATED`

### 2.5 Idempotency và concurrency

Mọi external mutation nhận `Idempotency-Key`. Mọi saga command có `commandId`. Participant lưu command result để retry trả lại cùng reply, không chỉ skip duplicate.

Các aggregate mutable có optimistic locking bằng `@Version`. Database có unique constraint cho business keys:

- `tickets(order_id)`
- `deliveries(order_id)`
- `authorizations(request_id)`
- `order_operations(idempotency_key, operation_type, principal_id)`
- processed command/event identity

Semantic lock cho Cancel/Revise được đặt atomically trước khi saga được khởi động.

### 2.6 Payment boundary

Accounting Service sở hữu payment lifecycle và cung cấp `PaymentProvider` interface. Implementation mặc định trong dev/test là deterministic sandbox provider; production provider được cấu hình bằng profile/secret.

Order chỉ lưu provider-neutral payment method reference, không lưu raw card/token. Payment authorization có thể được void trước capture; vì vậy authorize không được coi là pivot không thể compensation. Capture xảy ra tại business milestone được chọn và document rõ; default design là capture sau khi restaurant/kitchen chấp nhận order.

### 2.7 Security model

- Consumer identity lấy từ JWT `sub`/claim mapping, không lấy từ request body.
- Restaurant principal chỉ quản lý restaurant IDs được cấp quyền.
- Courier chỉ mutate delivery được assign cho chính họ.
- Admin endpoint tách route và audit log.
- Gateway relay token; downstream HTTP service cũng là OAuth2 Resource Server và tự enforce ownership.
- Internal network dùng mTLS/service identity và Kubernetes NetworkPolicy.
- Actuator public chỉ expose liveness/readiness với detail bị ẩn.

### 2.8 CQRS resilience

Order History consumer không đánh dấu processed khi prerequisite projection chưa tồn tại. Event out-of-order được lưu vào pending-event store hoặc retry topic, sau đó reconciliation worker áp dụng lại.

Scylla query model được thiết kế theo access pattern thay vì filter trong memory. Paging token lấy từ result page, không lấy từ request pageable. Keyword search không dùng Scylla set scan; nếu cần full text, dùng OpenSearch ở một phase riêng sau production baseline.

## 3. Luồng mục tiêu

### 3.1 Create Order

```text
Client -> Gateway -> Order Service
  1. Validate JWT, idempotency key, request shape
  2. Fetch authoritative menu snapshot from Restaurant
  3. Persist Order(APPROVAL_PENDING), OrderOperation(PENDING), OrderCreated outbox
  4. Start CreateOrderSaga
       a. Reserve consumer credit
       b. Create kitchen ticket idempotently
       c. Authorize payment idempotently
       d. Approve ticket idempotently
       e. Approve order and operation
  5. Emit OrderApproved
  6. Delivery creates one delivery per order
  7. Order History projects events with out-of-order recovery
```

Compensation trước approval:

- void payment authorization nếu đã authorize
- cancel ticket nếu đã tạo
- release reserved credit
- reject order và operation

### 3.2 Cancel Order

```text
HTTP local transaction:
  APPROVED -> CANCEL_PENDING
  create OrderOperation
  create/start CancelOrderSaga

Saga:
  begin kitchen cancel
  void/refund payment according to payment state
  release credit if reserved
  confirm kitchen cancel
  confirm order cancel and operation success
```

Duplicate request với cùng idempotency key trả lại operation cũ.

### 3.3 Revise Order

```text
HTTP local transaction:
  validate new authoritative menu snapshot
  APPROVED -> REVISION_PENDING
  persist pending revision + operation
  start ReviseOrderSaga

Saga:
  begin ticket revision
  reserve/release credit delta
  revise payment authorization idempotently
  confirm ticket revision
  apply order revision
  complete operation
```

Revision phải lưu old/new snapshot và delta để compensation chính xác.

## 4. Error handling

- Public REST errors dùng `application/problem+json` theo RFC 9457.
- Mỗi error có `type`, `title`, `status`, `detail`, `instance`, `errorCode`, `correlationId`.
- Retry chỉ áp dụng cho lỗi transient đã phân loại; validation/business rejection không retry.
- Kafka consumer dùng bounded exponential retry và dead-letter topic.
- Saga có timeout, stuck-saga metric và reconciliation command.
- Không swallow exception rồi đánh dấu processed.

## 5. Observability

Mọi HTTP request, saga command/reply và domain event mang:

- correlation ID
- causation ID
- W3C `traceparent`
- principal/service identity khi phù hợp

Metrics tối thiểu:

- order operation latency và result
- saga duration/failure/compensation/stuck count
- outbox backlog age/count
- Debezium connector status
- consumer lag/retry/DLT count
- payment authorization/capture/refund/reconciliation mismatch
- DB pool, query latency, optimistic-lock retry
- gateway downstream latency/error/rate-limit

Log phải JSON, mask token, email, address và payment data.

## 6. Deployment target

- Docker image pin bằng version/digest; chạy non-root, read-only filesystem.
- Kubernetes dùng Kustomize base + environment overlays.
- Mỗi workload có startup/readiness/liveness probes, requests/limits, PDB, HPA và topology spread.
- Secret lấy từ External Secrets/Vault production mode.
- Kafka dùng TLS/SASL/ACL; MySQL/Redis/Scylla không public port.
- Flyway chạy bằng migration job trước rollout.
- Backup/restore và DR drill có RPO/RTO được đo.

## 7. Test strategy

Mỗi task thực hiện TDD và commit nhỏ. Release gate gồm:

1. Unit/domain tests.
2. Migration tests trên MySQL sạch và upgrade path.
3. Testcontainers integration tests cho từng service.
4. Contract tests cho REST, command/reply và event schema.
5. Full E2E Create/Cancel/Revise bằng service thật, Kafka, Debezium, MySQL, Redis và Scylla.
6. Duplicate/retry/lost-reply/restart/out-of-order/poison-event tests.
7. Authorization and ownership tests.
8. Dependency, secret, SAST và container scans.
9. Load, soak và failure-injection tests.
10. Backup restore và rollback drill.

## 8. Phân rã chương trình

Chương trình được triển khai theo sáu phase có dependency tuyến tính:

1. Runtime Foundation and Messaging Recovery.
2. Business Correctness and Financial Safety.
3. Distributed Consistency and CQRS Reliability.
4. Security, Ownership and Public API Contract.
5. Production Platform and Observability.
6. Release Validation and Operational Readiness.

Mỗi phase có implementation plan riêng. Không bắt đầu phase sau khi acceptance gate của phase trước chưa đạt.

## 9. Ngoài phạm vi

Các mục sau không nằm trong chương trình baseline này:

- Tách thêm service hoặc chuyển sang service mesh khác.
- Thay Eventuate bằng framework orchestration khác.
- Multi-region active-active.
- Full-text search bằng OpenSearch.
- Mobile/web frontend.
- Recommendation, promotion, tax hoặc dynamic-pricing engine.

Các hạng mục này chỉ được xem xét sau khi baseline production gate đạt.