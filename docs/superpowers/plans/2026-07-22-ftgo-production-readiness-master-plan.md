# FTGO Production Readiness Master Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Đưa toàn bộ backend FTGO từ proof-of-concept sang production baseline có business correctness, distributed consistency, security, operability và release governance được kiểm chứng.

**Architecture:** Giữ nguyên service boundaries; dùng Eventuate Tram cho saga command/reply và custom transactional outbox + Debezium cho domain events. Triển khai theo acceptance gate tuyến tính; các subplan được đặt đúng dependency thay vì chỉ dựa vào số phase.

**Tech Stack:** Java 21, Spring Boot, Spring Cloud Gateway, Eventuate Tram Sagas, Kafka, Debezium, MySQL, Redis, ScyllaDB, Flyway, Testcontainers, Gradle, Kubernetes, Istio, OpenTelemetry, Micrometer, GitHub Actions.

## Global Constraints

- Target branch là `dev`; mỗi phase triển khai trên branch `agent/ftgo-phase-XX-*` và mở draft PR vào `dev`.
- Không thay đổi service boundaries trong chương trình hardening.
- Mọi production code change phải bắt đầu bằng failing test hoặc migration validation failure.
- Không lưu raw payment token, card data hoặc secret trong database, log, source control hay test fixture.
- Kafka key là aggregate ID; message/event identity là UUID riêng.
- Mọi external mutation phải hỗ trợ `Idempotency-Key`.
- Mọi API error dùng `application/problem+json` và có `correlationId`.
- Mọi task kết thúc bằng test command có expected result và một commit nhỏ.
- Không merge gate nếu acceptance checklist chưa đạt.

---

## Design Documents

1. [`../specs/2026-07-22-ftgo-production-readiness-design.md`](../specs/2026-07-22-ftgo-production-readiness-design.md)
2. [`../specs/2026-07-22-ftgo-payment-settlement-design-addendum.md`](../specs/2026-07-22-ftgo-payment-settlement-design-addendum.md)

## Program Dependency Graph

```text
Phase 01 Runtime Foundation
  -> Phase 01A Delivery Pickup Resolver
        |
        v
Phase 02 Business Correctness Core
  -> Phase 02A Pickup Address Snapshot
        |
        v
Phase 03 Distributed Consistency
        |
        v
Phase 02B Payment Settlement
        |
        v
Phase 04 Security and API
        |
        v
Phase 05 Platform and Observability
        |
        v
Phase 06 Release Validation
```

Phase 03 chạy trước Phase 02B để Payment Capture/Refund sử dụng shared processed-command result cache và không tạo migration version out-of-order.

## Plan Index

1. [`2026-07-22-phase-01-runtime-foundation.md`](./2026-07-22-phase-01-runtime-foundation.md)
2. [`2026-07-22-phase-01a-delivery-pickup-resolver.md`](./2026-07-22-phase-01a-delivery-pickup-resolver.md) — **thay thế Task 5 của Phase 01**.
3. [`2026-07-22-phase-02-business-correctness.md`](./2026-07-22-phase-02-business-correctness.md)
4. [`2026-07-22-phase-02a-pickup-address-snapshot.md`](./2026-07-22-phase-02a-pickup-address-snapshot.md) — chạy sau menu snapshot.
5. [`2026-07-22-phase-03-distributed-consistency.md`](./2026-07-22-phase-03-distributed-consistency.md)
6. [`2026-07-22-phase-02b-payment-settlement.md`](./2026-07-22-phase-02b-payment-settlement.md) — chạy sau Phase 03 Task 2.
7. [`2026-07-22-phase-04-security-api.md`](./2026-07-22-phase-04-security-api.md)
8. [`2026-07-22-phase-05-platform-observability.md`](./2026-07-22-phase-05-platform-observability.md)
9. [`2026-07-22-phase-06-release-validation.md`](./2026-07-22-phase-06-release-validation.md)

## Phase 01 Acceptance Gate

- [ ] `./gradlew clean test` compile và chạy được trên fresh checkout.
- [ ] Tất cả service khởi động từ database/volume rỗng.
- [ ] Eventuate schema production khớp dependency version và không dùng test-only schema.
- [ ] Kitchen entity schema validate thành công.
- [ ] Không còn duplicate saga local-step beans.
- [ ] Phase 01A cung cấp đúng một HTTP `RestaurantPickupAddressResolver` bean với timeout/retry bounded.
- [ ] Debezium connector route domain event đúng topic, key, event ID và event type.
- [ ] Smoke E2E chứng minh `OrderCreated` đi từ transaction tới Kafka consumer.

## Phase 02 Core Acceptance Gate

- [ ] Client không thể gửi name/price authoritative.
- [ ] Create/Revise lấy menu và pickup-address snapshot từ Restaurant Service.
- [ ] Order lưu immutable pickup-address snapshot.
- [ ] `OrderApproved` mang pickup/delivery address; Delivery không còn synchronous Restaurant dependency.
- [ ] Consumer credit được reserve/release idempotently.
- [ ] Accounting có provider abstraction và sandbox provider deterministic.
- [ ] Raw payment token không còn được persist hoặc log.
- [ ] Cancel/Revise semantic lock được đặt atomically trước saga execution.
- [ ] API trả operation resource cho Create/Cancel/Revise.
- [ ] Concurrent/duplicate requests không tạo order, ticket, authorization hoặc saga trùng.

## Phase 03 Acceptance Gate

- [ ] Saga participant retry trả cùng success reply sau lost-reply scenario.
- [ ] Domain event envelope có schema version, aggregate version và tracing metadata.
- [ ] Consumer dùng event ID thật, không dùng aggregate key làm message ID.
- [ ] Retry/DLT policy hoạt động với poison event.
- [ ] Order History phục hồi được out-of-order event.
- [ ] Scylla paging token và filtering đúng theo access pattern.
- [ ] Stuck saga/outbox backlog có metric và reconciliation command.

## Phase 02B Payment Acceptance Gate

- [ ] Ticket không thể chuyển `PREPARING` trước khi payment capture thành công.
- [ ] Capture Payment Saga xử lý success, decline, transient error và lost reply với tối đa một capture.
- [ ] Cancel Order voids authorization hoặc refunds captured payment đúng durable state.
- [ ] Provider webhook được verify signature, replay-safe và idempotent.
- [ ] Reconciliation sửa được monotonic pending state và tạo manual-review case cho ambiguity.
- [ ] Tổng refund không vượt captured amount.
- [ ] Payment settlement E2E suite chạy pass hai lần liên tiếp.

## Phase 04 Acceptance Gate

- [ ] Consumer không truy cập/cancel/revise order của consumer khác.
- [ ] Restaurant principal không quản lý restaurant khác.
- [ ] Courier không mutate delivery chưa assign cho mình.
- [ ] Downstream service tự validate JWT/ownership; không chỉ dựa vào Gateway.
- [ ] Public actuator không lộ dependency detail.
- [ ] REST API có versioning, request limits, idempotency và RFC 9457 errors.
- [ ] Security tests bao phủ BOLA/IDOR, forged identity và direct-service bypass.

## Phase 05 Acceptance Gate

- [ ] Docker images pin version/digest và chạy non-root.
- [ ] Kubernetes manifests có probes, requests/limits, HPA, PDB, topology spread và NetworkPolicy.
- [ ] Secrets không hardcode; dùng External Secrets/Vault production mode.
- [ ] Kafka/MySQL/Redis/Scylla không expose public port trong production overlay.
- [ ] Trace xuyên HTTP, saga, Kafka và outbox quan sát được.
- [ ] SLO dashboard và alert rules cho core flows tồn tại.
- [ ] Backup/restore procedure được chạy thử.

## Phase 06 Acceptance Gate

- [ ] GitHub Actions là required quality gate cho PR.
- [ ] Migration, contract, E2E, duplicate/retry/restart/out-of-order tests chạy tự động.
- [ ] SAST, dependency, secret, SBOM và container scans không có unresolved critical finding.
- [ ] Load/soak test đạt SLO đã định.
- [ ] Chaos tests chứng minh không mất event/payment state trong các failure scenario được hỗ trợ.
- [ ] Rollback và disaster recovery drill có evidence.
- [ ] Production readiness review ký duyệt checklist cuối.

## Execution Rules

- [ ] Mỗi phase bắt đầu bằng baseline branch từ `dev` mới nhất.
- [ ] Mỗi task dùng test-first, implementation tối thiểu, refactor sau khi green.
- [ ] Không gộp unrelated refactor vào task hardening.
- [ ] Contract thay đổi phải version hoặc duy trì backward compatibility trong ít nhất một release.
- [ ] Schema migration chỉ forward; rollback bằng application compatibility hoặc compensating migration, không sửa migration đã phát hành.
- [ ] Mọi PR body phải nêu failure mode được sửa, tests đã chạy và operational impact.
- [ ] Không thực thi Task 5 trong Phase 01; thực thi toàn bộ Phase 01A thay cho task đó.
- [ ] Phase 02A phải hoàn tất sau Phase 02 Task 2 và trước mọi E2E business-flow gate.
- [ ] Phase 02 Task 8 Full Business Flow Verification được hoãn đến sau Phase 02B để bao gồm capture/refund lifecycle.
- [ ] Phase 02B bắt đầu sau Phase 03 Task 2; dùng shared `IdempotentCommandExecutor`, không tạo một cơ chế processed-command thứ hai.

## Recommended Execution Mode

Dùng `superpowers:subagent-driven-development` cho từng task trong phase. Mỗi task qua hai review gate:

1. Spec/plan compliance review.
2. Code quality, tests và operational safety review.

Sau mỗi gate, chạy full verification suite và cập nhật acceptance checklist trước khi bắt đầu gate tiếp theo.
