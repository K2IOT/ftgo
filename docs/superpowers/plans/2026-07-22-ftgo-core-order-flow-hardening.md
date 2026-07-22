# FTGO Core Order Flow Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Finish the remaining production-readiness work for the FTGO core Order lifecycle on top of `dev`, without reimplementing the shared-contract, Order event, Kitchen preparation-guard, Delivery, or Order History work that is already present.

**Architecture:** Keep the existing Eventuate Tram orchestration and the repository's ADR-0001 decision that cross-service wire contracts live in `common`. Add a synchronous authoritative menu-quote boundary from Order Service to Restaurant Service before saga creation, replace Consumer credit checks with Order-scoped idempotent allocations, establish cancel/revise semantic locks in the API transaction, and verify the complete flow with focused module tests plus Testcontainers-backed integration tests.

**Tech Stack:** Java 21, Spring Boot 3.2, Spring MVC `RestClient`, Spring Data JPA, Flyway, Eventuate Tram/Sagas, Kafka, MySQL 8, Testcontainers 1.19.3, JUnit 5, Mockito, Awaitility, Gradle 8.5.

## Global Constraints

- Baseline branch: `dev`.
- Baseline commit: `4447a8e8f8fa1836da0f3ebf4c17e26c60bb2e60`.
- Preserve ADR-0001: serialized Eventuate commands, replies, and consumed integration events remain canonical classes in `common`.
- Do not introduce a separate integration-contracts module in this phase; `dev` has already standardized Order-flow wire contracts in `common`.
- Keep REST request/response DTOs service-local.
- Do not trust client-supplied menu item names or prices.
- Restaurant Service is authoritative for menu item identity, availability, name, unit price, and menu version.
- Do not replace Eventuate Tram, Kafka, Debezium, MySQL, or ScyllaDB.
- Do not refactor Delivery or Order History behavior already completed on `dev` unless a regression test proves this plan breaks it.
- Every behavioral change follows TDD: failing test, observed failure, minimal implementation, passing focused test, then commit.
- Every database change uses a new Flyway migration; never edit an already-applied migration during implementation.
- No task may claim success from historical output in `GRADLE_BUILD_REVIEW.md`; run the listed commands against the implementation branch.

---

## Dev Baseline: Already Completed and Excluded

The following work exists on `dev` and is a prerequisite, not part of this implementation scope:

- canonical shared Create/Cancel/Revise saga command contracts in `common`
- stable caller-provided Payment Authorization revision request IDs
- persisted `ticketId` and `authorizationId` on Order
- `OrderCreated` publication and Order History projection bootstrap
- Kitchen-owned refusal of cancel/revise after Preparation begins
- shared `OrderApproved` contract and idempotent Delivery creation
- canonical Order History consumption and contract guardrails

The implementation must preserve the focused tests added for those slices.

## Planned File Structure

### Restaurant authoritative quote boundary

- Create `restaurant-service/src/main/java/net/ftgo/restaurant/api/MenuQuoteRequest.java`
- Create `restaurant-service/src/main/java/net/ftgo/restaurant/api/MenuQuoteResponse.java`
- Create `restaurant-service/src/main/resources/db/migration/V2__add_restaurant_menu_version.sql`
- Modify `restaurant-service/src/main/java/net/ftgo/restaurant/domain/Restaurant.java`
- Modify `restaurant-service/src/main/java/net/ftgo/restaurant/repository/MenuItemRepository.java`
- Modify `restaurant-service/src/main/java/net/ftgo/restaurant/service/RestaurantService.java`
- Modify `restaurant-service/src/main/java/net/ftgo/restaurant/api/RestaurantController.java`
- Create `restaurant-service/src/test/java/net/ftgo/restaurant/service/RestaurantMenuQuoteTest.java`
- Create `restaurant-service/src/test/java/net/ftgo/restaurant/api/RestaurantMenuQuoteControllerTest.java`

### Order intake and revision

- Modify `order-service/src/main/java/net/ftgo/order/api/OrderLineItemRequest.java`
- Modify `order-service/src/main/java/net/ftgo/order/api/OrderController.java`
- Create `order-service/src/main/java/net/ftgo/order/service/OrderItemSelection.java`
- Create `order-service/src/main/java/net/ftgo/order/service/RestaurantMenuClient.java`
- Create `order-service/src/main/java/net/ftgo/order/service/RestaurantMenuQuote.java`
- Create `order-service/src/main/java/net/ftgo/order/service/HttpRestaurantMenuClient.java`
- Create `order-service/src/main/java/net/ftgo/order/config/RestaurantClientConfiguration.java`
- Modify `order-service/src/main/java/net/ftgo/order/service/OrderService.java`
- Modify `order-service/src/main/java/net/ftgo/order/domain/Order.java`
- Create `order-service/src/main/resources/db/migration/V2__add_menu_version_and_revision_number.sql`
- Create `order-service/src/test/java/net/ftgo/order/service/HttpRestaurantMenuClientTest.java`
- Modify `order-service/src/test/java/net/ftgo/order/service/OrderServiceTest.java`
- Create `order-service/src/test/java/net/ftgo/order/api/OrderAuthoritativePricingTest.java`

### Consumer credit allocation

- Create `consumer-service/src/main/java/net/ftgo/consumer/domain/CreditAllocation.java`
- Create `consumer-service/src/main/java/net/ftgo/consumer/domain/CreditAllocationState.java`
- Create `consumer-service/src/main/java/net/ftgo/consumer/repository/CreditAllocationRepository.java`
- Modify `consumer-service/src/main/java/net/ftgo/consumer/repository/ConsumerRepository.java`
- Modify `consumer-service/src/main/java/net/ftgo/consumer/service/ConsumerService.java`
- Modify `consumer-service/src/main/java/net/ftgo/consumer/messaging/ConsumerCommandHandlers.java`
- Create `consumer-service/src/main/resources/db/migration/V2__create_credit_allocations.sql`
- Create `consumer-service/src/test/java/net/ftgo/consumer/service/ConsumerCreditAllocationTest.java`
- Modify `consumer-service/src/test/java/net/ftgo/consumer/messaging/ConsumerCommandHandlersTest.java`

### Shared credit commands and saga wiring

- Create `common/src/main/java/net/ftgo/common/orderflow/commands/ReserveCreditCommand.java`
- Create `common/src/main/java/net/ftgo/common/orderflow/commands/ConfirmCreditCommand.java`
- Create `common/src/main/java/net/ftgo/common/orderflow/commands/ReleaseCreditCommand.java`
- Create `common/src/main/java/net/ftgo/common/orderflow/commands/AdjustCreditCommand.java`
- Create `common/src/main/java/net/ftgo/common/orderflow/commands/RestoreCreditCommand.java`
- Remove `common/src/main/java/net/ftgo/common/orderflow/commands/VerifyConsumerCommand.java`
- Remove `common/src/main/java/net/ftgo/common/orderflow/replies/ConsumerVerified.java`
- Modify `common/src/test/java/net/ftgo/common/orderflow/OrderFlowSharedContractsSerializationTest.java`
- Modify `common/src/test/java/net/ftgo/common/orderflow/SharedContractGuardrailsTest.java`
- Modify `order-service/src/main/java/net/ftgo/order/saga/CreateOrderSaga.java`
- Modify `order-service/src/main/java/net/ftgo/order/saga/CancelOrderSaga.java`
- Modify `order-service/src/main/java/net/ftgo/order/saga/ReviseOrderSaga.java`
- Modify `order-service/src/main/java/net/ftgo/order/saga/ReviseOrderSagaData.java`
- Modify saga and participant tests under `order-service/src/test/java/net/ftgo/order/saga/`

### Concurrency and verification

- Modify `order-service/src/main/java/net/ftgo/order/repository/OrderRepository.java`
- Modify `order-service/src/main/java/net/ftgo/order/saga/CancelOrderSagaLocalSteps.java`
- Modify `order-service/src/main/java/net/ftgo/order/saga/ReviseOrderSagaLocalSteps.java`
- Create `order-service/src/test/java/net/ftgo/order/service/OrderOperationConcurrencyTest.java`
- Create `order-service/src/test/java/net/ftgo/order/saga/CoreOrderFlowEndToEndTest.java`
- Create `.github/workflows/core-order-flow.yml`
- Modify `GETTING_STARTED.md`
- Create/update issue files under `.scratch/core-order-flow-hardening/`

---

### Task 1: Establish a Trustworthy Test and CI Baseline

**Files:**
- Modify: `consumer-service/src/test/java/net/ftgo/consumer/api/ConsumerControllerTest.java`
- Modify: `consumer-service/src/test/java/net/ftgo/consumer/messaging/ConsumerCommandHandlersTest.java`
- Modify: `consumer-service/src/test/java/net/ftgo/consumer/integration/ConsumerServiceIntegrationTest.java`
- Create: `.github/workflows/core-order-flow.yml`

**Interfaces:**
- Consumes: existing Spring Boot modules and Gradle tasks.
- Produces: a repeatable verification pipeline that later tasks can extend.

- [ ] **Step 1: Record the current failing baseline**

Run:

```bash
./gradlew clean :common:test :restaurant-service:test :consumer-service:test :order-service:test :kitchen-service:test :accounting-service:test :delivery-service:test :order-history-service:test
```

Expected before fixes: the command fails in the currently broken Consumer Spring test context, matching the historical `MessageProducer`/Eventuate configuration problem. Save the failing class names in the task notes; do not accept the historical 12/32 count as fresh evidence.

- [ ] **Step 2: Convert Consumer controller tests to a real MVC slice**

Use this class boundary instead of loading the full Eventuate application context:

```java
@WebMvcTest(ConsumerController.class)
@Import(ConsumerControllerTest.TestSecurityConfiguration.class)
class ConsumerControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    ConsumerService consumerService;

    @TestConfiguration
    static class TestSecurityConfiguration {
        @Bean
        SecurityFilterChain testSecurity(HttpSecurity http) throws Exception {
            return http.csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(registry -> registry.anyRequest().permitAll())
                .build();
        }
    }
}
```

Do not import Eventuate configuration into this test.

- [ ] **Step 3: Convert command-handler tests to plain unit tests**

Construct the handler directly:

```java
@ExtendWith(MockitoExtension.class)
class ConsumerCommandHandlersTest {

    @Mock
    ConsumerService consumerService;

    private ConsumerCommandHandlers handlers;

    @BeforeEach
    void setUp() {
        handlers = new ConsumerCommandHandlers(consumerService);
    }
}
```

Use Eventuate `CommandMessage` test fixtures only for the handler method under test; do not start Spring.

- [ ] **Step 4: Keep one infrastructure-backed Consumer integration test**

Annotate the integration class with the existing Testcontainers setup and import only the real Consumer persistence/service beans plus an explicit mocked `MessageProducer`:

```java
@TestConfiguration
static class EventuateProducerStub {
    @Bean
    MessageProducer messageProducer() {
        return mock(MessageProducer.class);
    }
}
```

Expected responsibility: prove Flyway migration, JPA mappings, pessimistic locking, and real transactions—not controller serialization.

- [ ] **Step 5: Run the focused baseline**

Run:

```bash
./gradlew :consumer-service:test :common:test :order-service:test
```

Expected: `BUILD SUCCESSFUL`, with zero failed tests.

- [ ] **Step 6: Add the CI workflow**

Create `.github/workflows/core-order-flow.yml`:

```yaml
name: Core Order Flow

on:
  pull_request:
    branches: [dev]
  push:
    branches: [dev]

permissions:
  contents: read

jobs:
  test:
    runs-on: ubuntu-latest
    timeout-minutes: 30
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
          cache: gradle
      - name: Make Gradle executable
        run: chmod +x gradlew
      - name: Contract and core-flow tests
        run: >-
          ./gradlew --no-daemon clean
          :common:test
          :restaurant-service:test
          :consumer-service:test
          :order-service:test
          :kitchen-service:test
          :accounting-service:test
          :delivery-service:test
          :order-history-service:test
```

- [ ] **Step 7: Commit**

```bash
git add consumer-service/src/test .github/workflows/core-order-flow.yml
git commit -m "test: establish core order flow verification baseline"
```

---

### Task 2: Add an Authoritative Restaurant Menu Quote API

**Files:**
- Create: `restaurant-service/src/main/java/net/ftgo/restaurant/api/MenuQuoteRequest.java`
- Create: `restaurant-service/src/main/java/net/ftgo/restaurant/api/MenuQuoteResponse.java`
- Create: `restaurant-service/src/main/resources/db/migration/V2__add_restaurant_menu_version.sql`
- Modify: `restaurant-service/src/main/java/net/ftgo/restaurant/domain/Restaurant.java`
- Modify: `restaurant-service/src/main/java/net/ftgo/restaurant/repository/MenuItemRepository.java`
- Modify: `restaurant-service/src/main/java/net/ftgo/restaurant/service/RestaurantService.java`
- Modify: `restaurant-service/src/main/java/net/ftgo/restaurant/api/RestaurantController.java`
- Test: `restaurant-service/src/test/java/net/ftgo/restaurant/service/RestaurantMenuQuoteTest.java`
- Test: `restaurant-service/src/test/java/net/ftgo/restaurant/api/RestaurantMenuQuoteControllerTest.java`

**Interfaces:**
- Consumes: `restaurantId` and a non-empty set of menu item IDs.
- Produces: `POST /restaurants/{restaurantId}/menu-quote` returning `restaurantId`, `menuVersion`, and authoritative items `{menuItemId, name, unitPrice}`.

- [ ] **Step 1: Write service tests for authoritative quoting**

Cover these exact cases:

```java
@Test
void quoteReturnsAuthoritativeNamePriceAndVersion() { }

@Test
void quoteRejectsMissingMenuItem() { }

@Test
void quoteRejectsUnavailableMenuItem() { }

@Test
void quoteRejectsDuplicateMenuItemIds() { }
```

The happy-path assertion must compare persisted `MenuItem` values, not request values.

- [ ] **Step 2: Run the tests and observe failure**

```bash
./gradlew :restaurant-service:test --tests '*RestaurantMenuQuoteTest'
```

Expected: compilation failure because `quoteMenu(...)`, quote DTOs, and `menuVersion` do not exist.

- [ ] **Step 3: Add the menu-version migration**

Create `V2__add_restaurant_menu_version.sql`:

```sql
ALTER TABLE restaurants
    ADD COLUMN menu_version BIGINT NOT NULL DEFAULT 1;
```

- [ ] **Step 4: Add menu version to Restaurant**

Add:

```java
@Column(name = "menu_version", nullable = false)
private Long menuVersion = 1L;

public void advanceMenuVersion() {
    this.menuVersion = this.menuVersion + 1;
    this.updatedAt = LocalDateTime.now();
}

public Long getMenuVersion() {
    return menuVersion;
}
```

Call `restaurant.advanceMenuVersion()` in the same transaction as every menu-item create, update, and delete, then save the Restaurant.

- [ ] **Step 5: Add a bulk repository query**

Add to `MenuItemRepository`:

```java
List<MenuItem> findByRestaurantIdAndIdIn(Long restaurantId, Collection<Long> ids);
```

- [ ] **Step 6: Create the REST DTOs**

`MenuQuoteRequest.java`:

```java
public record MenuQuoteRequest(
    @NotEmpty List<@NotNull Long> menuItemIds
) { }
```

`MenuQuoteResponse.java`:

```java
public record MenuQuoteResponse(
    Long restaurantId,
    Long menuVersion,
    List<Item> items
) {
    public record Item(Long menuItemId, String name, Money unitPrice) { }
}
```

- [ ] **Step 7: Implement RestaurantService.quoteMenu**

Add this exact contract:

```java
@Transactional(readOnly = true)
public MenuQuoteResponse quoteMenu(Long restaurantId, List<Long> requestedIds) {
    Restaurant restaurant = findRestaurant(restaurantId);
    LinkedHashSet<Long> uniqueIds = new LinkedHashSet<>(requestedIds);
    if (uniqueIds.size() != requestedIds.size()) {
        throw new IllegalArgumentException("Duplicate menu item IDs are not allowed");
    }

    Map<Long, MenuItem> itemsById = menuItemRepository
        .findByRestaurantIdAndIdIn(restaurantId, uniqueIds)
        .stream()
        .collect(Collectors.toMap(MenuItem::getId, Function.identity()));

    List<MenuQuoteResponse.Item> quotedItems = requestedIds.stream()
        .map(id -> {
            MenuItem item = Optional.ofNullable(itemsById.get(id))
                .orElseThrow(() -> new MenuItemNotFoundException(restaurantId, id));
            if (!item.isAvailable()) {
                throw new IllegalArgumentException("Menu item is unavailable: " + id);
            }
            return new MenuQuoteResponse.Item(item.getId(), item.getName(), item.getPrice());
        })
        .toList();

    return new MenuQuoteResponse(restaurantId, restaurant.getMenuVersion(), quotedItems);
}
```

- [ ] **Step 8: Add the controller endpoint**

```java
@PostMapping("/{restaurantId}/menu-quote")
public ResponseEntity<MenuQuoteResponse> quoteMenu(
        @PathVariable Long restaurantId,
        @Valid @RequestBody MenuQuoteRequest request) {
    return ResponseEntity.ok(restaurantService.quoteMenu(restaurantId, request.menuItemIds()));
}
```

- [ ] **Step 9: Run focused tests**

```bash
./gradlew :restaurant-service:test --tests '*RestaurantMenuQuoteTest' --tests '*RestaurantMenuQuoteControllerTest'
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 10: Commit**

```bash
git add restaurant-service
git commit -m "feat: add authoritative restaurant menu quotes"
```

---

### Task 3: Remove Client-Controlled Pricing from Create and Revise Order

**Files:**
- Modify: `order-service/src/main/java/net/ftgo/order/api/OrderLineItemRequest.java`
- Modify: `order-service/src/main/java/net/ftgo/order/api/OrderController.java`
- Create: `order-service/src/main/java/net/ftgo/order/service/OrderItemSelection.java`
- Create: `order-service/src/main/java/net/ftgo/order/service/RestaurantMenuClient.java`
- Create: `order-service/src/main/java/net/ftgo/order/service/RestaurantMenuQuote.java`
- Create: `order-service/src/main/java/net/ftgo/order/service/HttpRestaurantMenuClient.java`
- Create: `order-service/src/main/java/net/ftgo/order/config/RestaurantClientConfiguration.java`
- Modify: `order-service/src/main/java/net/ftgo/order/service/OrderService.java`
- Modify: `order-service/src/main/java/net/ftgo/order/domain/Order.java`
- Create: `order-service/src/main/resources/db/migration/V2__add_menu_version_and_revision_number.sql`
- Test: `order-service/src/test/java/net/ftgo/order/service/HttpRestaurantMenuClientTest.java`
- Test: `order-service/src/test/java/net/ftgo/order/api/OrderAuthoritativePricingTest.java`
- Modify: `order-service/src/test/java/net/ftgo/order/service/OrderServiceTest.java`

**Interfaces:**
- Consumes: `OrderItemSelection(menuItemId, quantity)` from REST requests.
- Produces: immutable Order line-item snapshots using the Restaurant quote; persists `menuVersion` and `revisionNumber`.

- [ ] **Step 1: Write tests that prove price tampering is impossible**

The JSON used by `OrderAuthoritativePricingTest` must contain only IDs and quantities:

```json
{
  "consumerId": 10,
  "restaurantId": 20,
  "lineItems": [{"menuItemId": 30, "quantity": 2}],
  "deliveryAddress": {
    "street": "1 Main St",
    "city": "Hanoi",
    "state": "HN",
    "zipCode": "100000"
  },
  "deliveryTime": "2026-07-23T12:00:00",
  "paymentToken": "tok_test"
}
```

Add a second request with `name` and `price` fields and assert they are ignored by Jackson or rejected by the configured unknown-property policy; in both cases they must never affect the stored total.

- [ ] **Step 2: Replace OrderLineItemRequest**

```java
public record OrderLineItemRequest(
    @NotNull Long menuItemId,
    @Positive int quantity
) { }
```

Delete `name` and `price` getters and all controller mapping that reads them.

- [ ] **Step 3: Add service-local selection and quote types**

```java
public record OrderItemSelection(Long menuItemId, int quantity) { }
```

```java
public record RestaurantMenuQuote(
    Long restaurantId,
    Long menuVersion,
    List<Item> items
) {
    public record Item(Long menuItemId, String name, Money unitPrice) { }
}
```

```java
public interface RestaurantMenuClient {
    RestaurantMenuQuote quote(Long restaurantId, List<Long> menuItemIds);
}
```

- [ ] **Step 4: Add the HTTP client**

```java
@Component
public class HttpRestaurantMenuClient implements RestaurantMenuClient {
    private final RestClient restClient;

    public HttpRestaurantMenuClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public RestaurantMenuQuote quote(Long restaurantId, List<Long> menuItemIds) {
        return restClient.post()
            .uri("/restaurants/{restaurantId}/menu-quote", restaurantId)
            .body(Map.of("menuItemIds", menuItemIds))
            .retrieve()
            .body(RestaurantMenuQuote.class);
    }
}
```

Configuration:

```java
@Configuration
public class RestaurantClientConfiguration {
    @Bean
    RestClient restaurantRestClient(
            RestClient.Builder builder,
            @Value("${ftgo.clients.restaurant.base-url}") String baseUrl) {
        return builder.baseUrl(baseUrl).build();
    }
}
```

Add to `order-service/src/main/resources/application.yml`:

```yaml
ftgo:
  clients:
    restaurant:
      base-url: ${RESTAURANT_SERVICE_URL:http://localhost:8083}
```

- [ ] **Step 5: Add Order persistence fields**

Migration:

```sql
ALTER TABLE orders
    ADD COLUMN menu_version BIGINT NOT NULL DEFAULT 1,
    ADD COLUMN revision_number BIGINT NOT NULL DEFAULT 0;
```

Order fields and behavior:

```java
@Column(name = "menu_version", nullable = false)
private Long menuVersion;

@Column(name = "revision_number", nullable = false)
private Long revisionNumber = 0L;

public long beginRevise(long quotedMenuVersion) {
    if (state != OrderState.APPROVED) {
        throw new IllegalStateException("Cannot revise order in state " + state + ". Expected APPROVED.");
    }
    state = OrderState.REVISION_PENDING;
    menuVersion = quotedMenuVersion;
    revisionNumber = revisionNumber + 1;
    updatedAt = LocalDateTime.now();
    return revisionNumber;
}
```

Set `menuVersion` in the creation constructor or a named factory that receives the authoritative quote.

- [ ] **Step 6: Build authoritative line items in OrderService**

Add:

```java
private List<OrderLineItem> quoteLineItems(
        Long restaurantId,
        List<OrderItemSelection> selections,
        Consumer<Long> menuVersionConsumer) {
    List<Long> ids = selections.stream().map(OrderItemSelection::menuItemId).toList();
    RestaurantMenuQuote quote = restaurantMenuClient.quote(restaurantId, ids);
    menuVersionConsumer.accept(quote.menuVersion());

    Map<Long, RestaurantMenuQuote.Item> quotedById = quote.items().stream()
        .collect(Collectors.toMap(RestaurantMenuQuote.Item::menuItemId, Function.identity()));

    return selections.stream()
        .map(selection -> {
            RestaurantMenuQuote.Item quoted = Optional.ofNullable(quotedById.get(selection.menuItemId()))
                .orElseThrow(() -> new IllegalArgumentException(
                    "Restaurant quote omitted menu item " + selection.menuItemId()));
            return new OrderLineItem(
                quoted.menuItemId(), quoted.name(), quoted.unitPrice(), selection.quantity());
        })
        .toList();
}
```

Refactor `createOrder(...)` and `reviseOrder(...)` to accept `List<OrderItemSelection>`, obtain a quote before constructing saga data, and never accept an `OrderLineItem` originating from the HTTP request.

- [ ] **Step 7: Update controller mapping**

```java
List<OrderItemSelection> selections = request.getLineItems().stream()
    .map(item -> new OrderItemSelection(item.menuItemId(), item.quantity()))
    .toList();
```

Use the same mapping for `ReviseOrderRequest`.

- [ ] **Step 8: Run focused tests**

```bash
./gradlew :order-service:test --tests '*OrderAuthoritativePricingTest' --tests '*OrderServiceTest' --tests '*HttpRestaurantMenuClientTest'
```

Expected: `BUILD SUCCESSFUL`; stored totals equal Restaurant prices times quantities.

- [ ] **Step 9: Commit**

```bash
git add order-service
git commit -m "feat: use authoritative menu pricing for orders"
```

---

### Task 4: Implement Order-Scoped Consumer Credit Allocations

**Files:**
- Create: `consumer-service/src/main/java/net/ftgo/consumer/domain/CreditAllocation.java`
- Create: `consumer-service/src/main/java/net/ftgo/consumer/domain/CreditAllocationState.java`
- Create: `consumer-service/src/main/java/net/ftgo/consumer/repository/CreditAllocationRepository.java`
- Modify: `consumer-service/src/main/java/net/ftgo/consumer/repository/ConsumerRepository.java`
- Modify: `consumer-service/src/main/java/net/ftgo/consumer/service/ConsumerService.java`
- Create: `consumer-service/src/main/resources/db/migration/V2__create_credit_allocations.sql`
- Test: `consumer-service/src/test/java/net/ftgo/consumer/service/ConsumerCreditAllocationTest.java`

**Interfaces:**
- Consumes: stable business keys `orderId` and `revisionNumber`.
- Produces: idempotent `reserve`, `confirm`, `release`, `adjust`, and `restore` operations.

- [ ] **Step 1: Write allocation tests**

Required tests:

```java
@Test void reserveDecrementsAvailableCreditOnce() { }
@Test void duplicateReserveReturnsExistingAllocation() { }
@Test void concurrentReservationsCannotOverspendCredit() { }
@Test void confirmChangesReservedToCommitted() { }
@Test void releaseRestoresCreditExactlyOnce() { }
@Test void adjustChangesAmountOnceForRevisionNumber() { }
@Test void restoreReturnsToPreviousAmountOnce() { }
```

- [ ] **Step 2: Add the migration**

```sql
CREATE TABLE credit_allocations (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    consumer_id BIGINT NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    state VARCHAR(32) NOT NULL,
    previous_amount DECIMAL(10,2),
    pending_revision_number BIGINT,
    version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_credit_allocations_order UNIQUE (order_id),
    CONSTRAINT fk_credit_allocations_consumer
        FOREIGN KEY (consumer_id) REFERENCES consumers(id),
    INDEX idx_credit_allocations_consumer_state (consumer_id, state)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

- [ ] **Step 3: Implement the state model**

```java
public enum CreditAllocationState {
    RESERVED,
    COMMITTED,
    RELEASED
}
```

`CreditAllocation` must expose these methods:

```java
public static CreditAllocation reserve(Long orderId, Long consumerId, Money amount);
public void confirm();
public Money release();
public Money adjust(long revisionNumber, Money newAmount);
public Money restore(long revisionNumber);
```

Behavior:

- duplicate `confirm` on `COMMITTED` is a no-op
- duplicate `release` on `RELEASED` returns `Money.ZERO`
- duplicate `adjust` with the same `revisionNumber` returns `Money.ZERO`
- `restore` only applies to the currently pending revision and then clears `previousAmount` and `pendingRevisionNumber`

- [ ] **Step 4: Add pessimistic Consumer locking**

Add to `ConsumerRepository`:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select c from Consumer c where c.id = :id")
Optional<Consumer> findByIdWithLock(@Param("id") Long id);
```

- [ ] **Step 5: Implement ConsumerService operations**

Required signatures:

```java
@Transactional
public CreditAllocation reserveCredit(Long orderId, Long consumerId, Money amount);

@Transactional
public void confirmCredit(Long orderId);

@Transactional
public void releaseCredit(Long orderId);

@Transactional
public void adjustCredit(Long orderId, long revisionNumber, Money newAmount);

@Transactional
public void restoreCredit(Long orderId, long revisionNumber);
```

`reserveCredit` must:

1. return an existing allocation for the same `orderId` when consumer and amount match
2. reject reuse of `orderId` with different consumer or amount
3. lock the Consumer row
4. call `consumer.reserveCredit(amount)`
5. persist Consumer and allocation in one transaction

`adjustCredit` computes `delta = newAmount - currentAmount`; reserve positive delta or release the absolute negative delta under the same Consumer lock.

- [ ] **Step 6: Fix credit-limit decrease while touching the invariant**

Replace subtraction that rejects negative values with explicit reserved-credit validation:

```java
public void updateCreditLimit(Money newCreditLimit) {
    validateCreditLimit(newCreditLimit);
    Money reserved = creditLimit.subtract(availableCredit);
    if (newCreditLimit.isLessThan(reserved)) {
        throw new IllegalArgumentException("Credit limit cannot be lower than reserved credit");
    }
    creditLimit = newCreditLimit;
    availableCredit = newCreditLimit.subtract(reserved);
    updatedAt = LocalDateTime.now();
}
```

- [ ] **Step 7: Run focused tests**

```bash
./gradlew :consumer-service:test --tests '*ConsumerCreditAllocationTest'
```

Expected: `BUILD SUCCESSFUL`, including the concurrent overspend test.

- [ ] **Step 8: Commit**

```bash
git add consumer-service
git commit -m "feat: add idempotent consumer credit allocations"
```

---

### Task 5: Add Canonical Credit Commands and Participant Handlers

**Files:**
- Create five command classes under `common/src/main/java/net/ftgo/common/orderflow/commands/`
- Remove: `VerifyConsumerCommand.java`
- Remove: `ConsumerVerified.java`
- Modify: `consumer-service/src/main/java/net/ftgo/consumer/messaging/ConsumerCommandHandlers.java`
- Modify: shared contract tests in `common`
- Modify: `consumer-service/src/test/java/net/ftgo/consumer/messaging/ConsumerCommandHandlersTest.java`

**Interfaces:**
- Consumes: Eventuate commands from Order sagas.
- Produces: success/failure replies with idempotent Consumer-side mutations.

- [ ] **Step 1: Write serialization tests before creating commands**

Round-trip these exact schemas:

```text
ReserveCreditCommand(orderId: Long, consumerId: Long, amount: Money)
ConfirmCreditCommand(orderId: Long)
ReleaseCreditCommand(orderId: Long)
AdjustCreditCommand(orderId: Long, revisionNumber: long, newAmount: Money)
RestoreCreditCommand(orderId: Long, revisionNumber: long)
```

Expected initial result: compilation failure.

- [ ] **Step 2: Implement the five POJO commands**

Each class must:

- implement `io.eventuate.tram.commands.common.Command`
- provide a public no-argument constructor
- provide a full-argument constructor
- provide getters and setters for every field

Example complete shape for `ReserveCreditCommand`:

```java
public class ReserveCreditCommand implements Command {
    private Long orderId;
    private Long consumerId;
    private Money amount;

    public ReserveCreditCommand() { }

    public ReserveCreditCommand(Long orderId, Long consumerId, Money amount) {
        this.orderId = orderId;
        this.consumerId = consumerId;
        this.amount = amount;
    }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getConsumerId() { return consumerId; }
    public void setConsumerId(Long consumerId) { this.consumerId = consumerId; }
    public Money getAmount() { return amount; }
    public void setAmount(Money amount) { this.amount = amount; }
}
```

Use the exact fields listed in Step 1 for the other four classes; add no extra timestamps, random IDs, or service-local entities.

- [ ] **Step 3: Register all handlers**

```java
return SagaCommandHandlersBuilder
    .fromChannel(ChannelNames.CONSUMER_SERVICE_COMMAND_CHANNEL)
    .onMessage(ReserveCreditCommand.class, this::handleReserveCredit)
    .onMessage(ConfirmCreditCommand.class, this::handleConfirmCredit)
    .onMessage(ReleaseCreditCommand.class, this::handleReleaseCredit)
    .onMessage(AdjustCreditCommand.class, this::handleAdjustCredit)
    .onMessage(RestoreCreditCommand.class, this::handleRestoreCredit)
    .build();
```

Each handler delegates once to `ConsumerService`; business exceptions return `withFailure(e.getMessage())`, while success returns `withSuccess()`.

- [ ] **Step 4: Remove obsolete verify-only contracts**

Delete `VerifyConsumerCommand` and `ConsumerVerified`. Update guardrail tests so reintroducing either class or using package-local credit commands fails the build.

- [ ] **Step 5: Run contract and participant tests**

```bash
./gradlew :common:test :consumer-service:test --tests '*ConsumerCommandHandlersTest'
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add common consumer-service
git commit -m "feat: add shared credit allocation commands"
```

---

### Task 6: Wire Credit Allocation into Create, Cancel, and Revise Sagas

**Files:**
- Modify: `order-service/src/main/java/net/ftgo/order/saga/CreateOrderSaga.java`
- Modify: `order-service/src/main/java/net/ftgo/order/saga/CancelOrderSaga.java`
- Modify: `order-service/src/main/java/net/ftgo/order/saga/ReviseOrderSaga.java`
- Modify: `order-service/src/main/java/net/ftgo/order/saga/ReviseOrderSagaData.java`
- Modify: saga tests and `TestParticipantConfiguration.java`

**Interfaces:**
- Consumes: shared credit commands from Task 5.
- Produces: credit state consistent with saga outcome and retry behavior.

- [ ] **Step 1: Write failing saga-definition tests**

Assert command order:

```text
Create: ReserveCredit -> CreateTicket -> AuthorizeCard [pivot] -> ApproveTicket -> ConfirmCredit -> ApproveOrder
Cancel: BeginCancelTicket -> ReverseAuthorization [pivot] -> ConfirmCancelTicket -> ReleaseCredit -> ConfirmCancel
Revise: BeginReviseTicket -> AdjustCredit -> ReviseAuthorization [pivot] -> ConfirmReviseTicket -> ConfirmReviseOrder
```

Assert Create compensation sends `ReleaseCreditCommand` when Ticket creation or authorization fails. Assert Revise compensation sends `RestoreCreditCommand` when Accounting revision fails.

- [ ] **Step 2: Replace verify with reserve in CreateOrderSaga**

```java
private CommandWithDestination reserveCredit(CreateOrderSagaData data) {
    return send(new ReserveCreditCommand(
            data.getOrderId(), data.getConsumerId(), data.getOrderTotal()))
        .to(ChannelNames.CONSUMER_SERVICE_COMMAND_CHANNEL)
        .build();
}

private CommandWithDestination releaseCredit(CreateOrderSagaData data) {
    return send(new ReleaseCreditCommand(data.getOrderId()))
        .to(ChannelNames.CONSUMER_SERVICE_COMMAND_CHANNEL)
        .build();
}

private CommandWithDestination confirmCredit(CreateOrderSagaData data) {
    return send(new ConfirmCreditCommand(data.getOrderId()))
        .to(ChannelNames.CONSUMER_SERVICE_COMMAND_CHANNEL)
        .build();
}
```

Definition:

```java
step().invokeLocal(this::createOrder).withCompensation(this::rejectOrder)
.step().invokeParticipant(this::reserveCredit).withCompensation(this::releaseCredit)
.step().invokeParticipant(this::createTicket)
    .onReply(TicketCreated.class, this::handleCreateTicketReply)
    .withCompensation(this::cancelTicket)
.step().invokeParticipant(this::authorizeCard)
    .onReply(CardAuthorized.class, this::handleAuthorizeCardReply)
.step().invokeParticipant(this::approveTicket)
.step().invokeParticipant(this::confirmCredit)
.step().invokeParticipant(this::approveOrderStep)
.build();
```

- [ ] **Step 3: Release credit in CancelOrderSaga after the pivot**

Add after ticket confirmation:

```java
private CommandWithDestination releaseCredit(CancelOrderSagaData data) {
    return send(new ReleaseCreditCommand(data.getOrderId()))
        .to(ChannelNames.CONSUMER_SERVICE_COMMAND_CHANNEL)
        .build();
}
```

Because this is post-pivot, it is retried forward and has no compensation.

- [ ] **Step 4: Adjust and restore credit in ReviseOrderSaga**

Add `revisionNumber` to `ReviseOrderSagaData` and send:

```java
private CommandWithDestination adjustCredit(ReviseOrderSagaData data) {
    return send(new AdjustCreditCommand(
            data.getOrderId(), data.getRevisionNumber(), data.getRevisedOrderTotal()))
        .to(ChannelNames.CONSUMER_SERVICE_COMMAND_CHANNEL)
        .build();
}

private CommandWithDestination restoreCredit(ReviseOrderSagaData data) {
    return send(new RestoreCreditCommand(data.getOrderId(), data.getRevisionNumber()))
        .to(ChannelNames.CONSUMER_SERVICE_COMMAND_CHANNEL)
        .build();
}
```

Place this step after Kitchen accepts revision and before the Accounting pivot.

- [ ] **Step 5: Use stable revision identity**

Replace the line-item signature request ID with:

```java
String paymentRevisionRequestId(Long orderId, long revisionNumber) {
    return "revise-auth-" + orderId + "-" + revisionNumber;
}
```

- [ ] **Step 6: Update participant test configuration**

Register the real `ConsumerCommandHandlers.commandHandlers()` and remove mock handlers for the deleted verify command. Saga integration tests must import the same shared command classes used by production handlers.

- [ ] **Step 7: Run saga tests**

```bash
./gradlew :order-service:test --tests '*CreateOrderSaga*' --tests '*CancelOrderSaga*' --tests '*ReviseOrderSaga*' :consumer-service:test --tests '*ConsumerCommandHandlersTest'
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```bash
git add order-service consumer-service common
git commit -m "feat: coordinate credit allocation through order sagas"
```

---

### Task 7: Establish Cancel/Revise Locks Before Saga Creation

**Files:**
- Modify: `order-service/src/main/java/net/ftgo/order/service/OrderService.java`
- Modify: `order-service/src/main/java/net/ftgo/order/saga/CancelOrderSaga.java`
- Modify: `order-service/src/main/java/net/ftgo/order/saga/ReviseOrderSaga.java`
- Modify: `order-service/src/main/java/net/ftgo/order/saga/CancelOrderSagaLocalSteps.java`
- Modify: `order-service/src/main/java/net/ftgo/order/saga/ReviseOrderSagaLocalSteps.java`
- Test: `order-service/src/test/java/net/ftgo/order/service/OrderOperationConcurrencyTest.java`

**Interfaces:**
- Consumes: an approved Order and a cancel/revise request.
- Produces: one pending-state transition and one saga instance; conflicting requests return deterministic conflict and never start a second saga.

- [ ] **Step 1: Write concurrency tests**

Use two transactions released by a `CountDownLatch` and assert:

```java
@Test void concurrentCancelAndReviseStartOnlyOneSaga() { }
@Test void duplicateCancelWhileCancelPendingDoesNotStartSecondSaga() { }
@Test void duplicateReviseWhileRevisionPendingDoesNotStartSecondSaga() { }
@Test void failedSagaCreationRollsBackPendingState() { }
```

Verify `SagaInstanceFactory.create(...)` invocation count with Mockito in service tests and verify persisted state in the transaction-backed test.

- [ ] **Step 2: Lock and transition in OrderService.cancelOrder**

```java
@Transactional
public void cancelOrder(Long orderId) {
    Order order = orderRepository.findByIdWithLock(orderId)
        .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));
    requireDownstreamReferences(order);
    order.beginCancel();
    orderRepository.saveAndFlush(order);

    sagaInstanceFactory.create(cancelOrderSaga, new CancelOrderSagaData(
        order.getId(), order.getConsumerId(), order.getTicketId(), order.getAuthorizationId()));
}
```

`requireDownstreamReferences` throws `IllegalStateException` when `ticketId` or `authorizationId` is null.

- [ ] **Step 3: Lock, quote, increment revision, and transition in reviseOrder**

The transaction must:

1. obtain the authoritative Restaurant quote
2. lock Order with `findByIdWithLock`
3. call `order.beginRevise(quote.menuVersion())` and capture the returned revision number
4. save and flush the pending state
5. create `ReviseOrderSagaData` with the revision number and stable payment request ID

A rollback from `SagaInstanceFactory.create` must restore the previous Order state automatically.

- [ ] **Step 4: Make saga first local steps validation-only**

The first local function must not transition again:

```java
private void beginCancel(CancelOrderSagaData data) {
    localSteps.requireState(data.getOrderId(), OrderState.CANCEL_PENDING);
}
```

```java
private void beginRevise(ReviseOrderSagaData data) {
    localSteps.requireState(data.getOrderId(), OrderState.REVISION_PENDING);
}
```

Keep compensation methods `undoCancelOrder` and `undoReviseOrder`. Remove unused serialized `BeginCancelCommand` and `BeginReviseCommand` handlers if they are no longer sent through Eventuate.

- [ ] **Step 5: Return deterministic API conflicts**

Keep HTTP 409 and use stable error codes:

```text
ORDER_OPERATION_IN_PROGRESS
ORDER_NOT_APPROVED
ORDER_REFERENCES_MISSING
```

The response must include `orderId`, current state, and requested operation. Do not automatically retry an API mutation that can create another saga.

- [ ] **Step 6: Run concurrency and saga tests**

```bash
./gradlew :order-service:test --tests '*OrderOperationConcurrencyTest' --tests '*CancelOrderSaga*' --tests '*ReviseOrderSaga*'
```

Expected: `BUILD SUCCESSFUL`; each concurrent test records one saga creation.

- [ ] **Step 7: Commit**

```bash
git add order-service
git commit -m "fix: lock order before cancel and revise sagas"
```

---

### Task 8: Prove the Full Core Flow and Update Operational Documentation

**Files:**
- Create: `order-service/src/test/java/net/ftgo/order/saga/CoreOrderFlowEndToEndTest.java`
- Modify: `.github/workflows/core-order-flow.yml`
- Modify: `GETTING_STARTED.md`
- Create: `.scratch/core-order-flow-hardening/PRD.md`
- Create/update: `.scratch/core-order-flow-hardening/issues/*.md`

**Interfaces:**
- Consumes: all behavior implemented in Tasks 1-7.
- Produces: executable evidence for Create, Cancel, and Revise across real persistence and message contracts.

- [ ] **Step 1: Add end-to-end acceptance scenarios**

The Testcontainers suite must cover:

```java
@Test void createsOrderUsingRestaurantPriceAndCommitsCredit() { }
@Test void rejectsUnavailableMenuItemBeforeOrderPersistence() { }
@Test void rejectsInsufficientCreditWithoutCreatingTicket() { }
@Test void retriesDuplicateReserveAndAuthorizeWithoutDoubleMutation() { }
@Test void cancelsApprovedOrderAndReleasesCredit() { }
@Test void refusesCancelAfterPreparationWithoutReversingAuthorization() { }
@Test void revisesApprovedOrderAndAdjustsCredit() { }
@Test void compensatesCreditWhenPaymentRevisionFails() { }
@Test void refusesReviseAfterPreparationWithoutChangingCreditOrAuthorization() { }
@Test void concurrentCancelAndReviseProduceOneWinningOperation() { }
```

Use real shared contract classes and real participant command-handler registrations. Mocks may inject deterministic participant failure, but must not replace the wire contract or handler type.

- [ ] **Step 2: Verify outbox facts**

For successful paths, assert one row each for the appropriate Order lifecycle events. For refused cancel/revise attempts, assert no new Order integration event is written.

- [ ] **Step 3: Run the full verification matrix**

```bash
./gradlew clean test
```

Expected: `BUILD SUCCESSFUL`, zero failed tests.

Then run:

```bash
docker compose config
```

Expected: exit code 0.

Then start infrastructure and services using the documented profiles and verify:

```bash
curl --fail http://localhost:8080/actuator/health
curl --fail http://localhost:8081/actuator/health
curl --fail http://localhost:8082/actuator/health
curl --fail http://localhost:8084/actuator/health
curl --fail http://localhost:8085/actuator/health
curl --fail http://localhost:8086/actuator/health
```

Expected: every response contains `"status":"UP"`. Use the actual documented ports after resolving any current profile mapping; do not copy these examples blindly if `GETTING_STARTED.md` defines different ports.

- [ ] **Step 4: Extend CI with the end-to-end class**

Add a separate job with a 45-minute timeout:

```yaml
  end-to-end:
    runs-on: ubuntu-latest
    timeout-minutes: 45
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
          cache: gradle
      - run: chmod +x gradlew
      - run: ./gradlew --no-daemon :order-service:test --tests '*CoreOrderFlowEndToEndTest'
```

- [ ] **Step 5: Update repository tracking docs**

Create `.scratch/core-order-flow-hardening/PRD.md` with the implemented scope and one issue file per task. Mark an issue `done` only after its listed verification command has been run successfully.

Update `GETTING_STARTED.md` with:

- Restaurant Service URL used by Order Service
- menu-quote request example
- create/revise requests containing only item IDs and quantities
- required infrastructure startup order
- full verification commands

- [ ] **Step 6: Final regression check against completed dev slices**

Run:

```bash
./gradlew \
  :common:test \
  :order-service:test \
  :consumer-service:test \
  :restaurant-service:test \
  :kitchen-service:test \
  :accounting-service:test \
  :delivery-service:test \
  :order-history-service:test
```

Expected: `BUILD SUCCESSFUL` and no regression in shared-contract guardrails, Delivery creation, or Order History projections.

- [ ] **Step 7: Commit**

```bash
git add order-service/src/test .github/workflows/core-order-flow.yml GETTING_STARTED.md .scratch/core-order-flow-hardening
git commit -m "test: verify hardened core order flow end to end"
```

---

## Phase 1 Exit Criteria

Phase 1 is complete only when fresh command output proves all of the following:

- Create and Revise REST contracts contain menu item IDs and quantities only.
- Order totals and snapshots use Restaurant Service names and prices.
- Restaurant quote failures occur before Order persistence or saga creation.
- Consumer credit is reserved per Order and cannot be overspent concurrently.
- Create compensation releases reserved credit.
- Create success commits the credit allocation.
- Cancel success releases committed credit after payment reversal.
- Revise adjusts credit idempotently by `revisionNumber` and restores it on pre-pivot compensation.
- Cancel/Revise pending states are persisted before remote participant work.
- Concurrent or repeated lifecycle requests do not start duplicate sagas.
- Existing shared-contract, Delivery, and Order History tests remain green.
- `./gradlew clean test` succeeds.
- The GitHub Actions workflow runs the same focused and end-to-end verification paths.

## Follow-On Plans

Do not fold these independent subsystems into this plan. Create separate design/spec/plan cycles after Phase 1:

1. Event backbone hardening: standard event envelope, event IDs, Debezium routing, dead-letter and replay.
2. Gateway and resource authorization: subject-to-domain identity, BOLA/IDOR prevention, internal endpoint boundaries.
3. Infrastructure and operations: secrets, pinned images, Kafka security, observability, readiness, outbox retention.
4. Reliability and performance: Scylla query redesign, load tests, restart recovery, partitioning, timeout tuning, runbooks.
