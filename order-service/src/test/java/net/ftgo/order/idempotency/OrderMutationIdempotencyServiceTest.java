package net.ftgo.order.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.ftgo.common.Address;
import net.ftgo.common.Money;
import net.ftgo.order.api.CreateOrderRequest;
import net.ftgo.order.api.OrderLineItemRequest;
import net.ftgo.order.api.ReviseOrderRequest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderMutationIdempotencyServiceTest {

    private static final LocalDateTime DELIVERY_TIME = LocalDateTime.now()
        .plusHours(2)
        .withNano(0);

    private final InMemoryApiIdempotencyStore store = new InMemoryApiIdempotencyStore();
    private final OrderMutationIdempotencyService service = new OrderMutationIdempotencyService(
        store,
        new ObjectMapper().findAndRegisterModules()
    );

    @Test
    void duplicateRequestReplaysWithoutRunningMutationAgain() {
        AtomicInteger mutations = new AtomicInteger();
        byte[] hash = new byte[] {1, 2, 3};

        IdempotentResult<String> first = service.execute(
            101L,
            OrderMutationIdempotencyService.CREATE_ORDER,
            "create-101-1",
            hash,
            () -> {
                mutations.incrementAndGet();
                return "{\"orderId\":9001}";
            }
        );
        IdempotentResult<String> replay = service.execute(
            101L,
            OrderMutationIdempotencyService.CREATE_ORDER,
            "create-101-1",
            hash,
            () -> {
                mutations.incrementAndGet();
                return "{\"orderId\":9002}";
            }
        );

        assertEquals(1, mutations.get());
        assertEquals(201, first.httpStatus());
        assertEquals(first.responseBody(), replay.responseBody());
        assertEquals(9001L, replay.resourceId());
        assertFalse(first.replayed());
        assertTrue(replay.replayed());
    }

    @Test
    void duplicateCancelReplaysWithoutStartingSecondMutation() {
        AtomicInteger mutations = new AtomicInteger();
        byte[] hash = service.hashCancel(101L, 9001L);
        String operation = OrderMutationIdempotencyService.cancelOperation(9001L);

        IdempotentResult<String> first = service.execute(
            101L,
            operation,
            "cancel-9001-1",
            hash,
            () -> {
                mutations.incrementAndGet();
                return null;
            }
        );
        IdempotentResult<String> replay = service.execute(
            101L,
            operation,
            "cancel-9001-1",
            hash,
            () -> {
                mutations.incrementAndGet();
                return null;
            }
        );

        assertEquals(1, mutations.get());
        assertEquals(200, first.httpStatus());
        assertEquals(first.responseBody(), replay.responseBody());
        assertFalse(first.replayed());
        assertTrue(replay.replayed());
    }

    @Test
    void duplicateRevisionReplaysWithoutStartingSecondMutation() {
        AtomicInteger mutations = new AtomicInteger();
        ReviseOrderRequest revision = new ReviseOrderRequest(List.of(
            new OrderLineItemRequest(1L, "Pizza", new Money("20.00"), 2)
        ));
        byte[] hash = service.hashRevise(101L, 9001L, revision);
        String operation = OrderMutationIdempotencyService.reviseOperation(9001L);

        IdempotentResult<String> first = service.execute(
            101L,
            operation,
            "revise-9001-1",
            hash,
            () -> {
                mutations.incrementAndGet();
                return null;
            }
        );
        IdempotentResult<String> replay = service.execute(
            101L,
            operation,
            "revise-9001-1",
            hash,
            () -> {
                mutations.incrementAndGet();
                return null;
            }
        );

        assertEquals(1, mutations.get());
        assertEquals(200, first.httpStatus());
        assertEquals(first.responseBody(), replay.responseBody());
        assertFalse(first.replayed());
        assertTrue(replay.replayed());
    }

    @Test
    void changedRequestHashIsRejectedBeforeMutation() {
        AtomicInteger mutations = new AtomicInteger();
        service.execute(
            101L,
            OrderMutationIdempotencyService.CREATE_ORDER,
            "create-101-2",
            new byte[] {1},
            () -> "{\"orderId\":9001}"
        );

        assertThrows(IdempotencyKeyConflictException.class, () -> service.execute(
            101L,
            OrderMutationIdempotencyService.CREATE_ORDER,
            "create-101-2",
            new byte[] {2},
            () -> {
                mutations.incrementAndGet();
                return "{\"orderId\":9002}";
            }
        ));
        assertEquals(0, mutations.get());
    }

    @Test
    void existingProcessingClaimDoesNotRunMutationAgain() {
        AtomicInteger mutations = new AtomicInteger();
        byte[] hash = new byte[] {7, 8, 9};
        store.seedProcessing(
            101L,
            OrderMutationIdempotencyService.CREATE_ORDER,
            "create-in-flight",
            hash
        );

        assertThrows(IdempotencyRequestInProgressException.class, () -> service.execute(
            101L,
            OrderMutationIdempotencyService.CREATE_ORDER,
            "create-in-flight",
            hash,
            () -> {
                mutations.incrementAndGet();
                return "{\"orderId\":9001}";
            }
        ));
        assertEquals(0, mutations.get());
    }

    @Test
    void canonicalCreateHashExcludesPaymentTokenButIncludesBusinessPayload() {
        CreateOrderRequest first = createRequest("tok_secret_one", 1);
        CreateOrderRequest sameBusinessRequest = createRequest("tok_secret_two", 1);
        CreateOrderRequest changedQuantity = createRequest("tok_secret_two", 2);

        byte[] firstHash = service.hashCreate(101L, first);
        assertArrayEquals(firstHash, service.hashCreate(101L, sameBusinessRequest));
        assertFalse(java.util.Arrays.equals(
            firstHash,
            service.hashCreate(101L, changedQuantity)
        ));
    }

    @Test
    void canonicalHashesBindCancelAndReviseToConsumerAndOrder() {
        ReviseOrderRequest revision = new ReviseOrderRequest(List.of(
            new OrderLineItemRequest(1L, "Pizza", new Money("20.00"), 2)
        ));

        assertFalse(java.util.Arrays.equals(
            service.hashCancel(101L, 9001L),
            service.hashCancel(202L, 9001L)
        ));
        assertFalse(java.util.Arrays.equals(
            service.hashRevise(101L, 9001L, revision),
            service.hashRevise(101L, 9002L, revision)
        ));
    }

    private CreateOrderRequest createRequest(String paymentToken, int quantity) {
        return new CreateOrderRequest(
            456L,
            7L,
            List.of(new OrderLineItemRequest(1L, "Pizza", new Money("20.00"), quantity)),
            new Address("123 Main St", "San Francisco", "CA", "94102"),
            DELIVERY_TIME,
            paymentToken
        );
    }

    private static final class InMemoryApiIdempotencyStore implements ApiIdempotencyStore {
        private ApiIdempotencyRecord record;

        void seedProcessing(Long consumerId, String operation, String key, byte[] requestHash) {
            Instant now = Instant.now();
            record = ApiIdempotencyRecord.processing(
                consumerId,
                operation,
                key,
                requestHash,
                now,
                now.plusSeconds(3600)
            );
        }

        @Override
        public boolean insertProcessing(
            Long consumerId,
            String operation,
            String key,
            byte[] requestHash,
            Instant expiresAt
        ) {
            if (record != null) {
                return false;
            }
            record = ApiIdempotencyRecord.processing(
                consumerId,
                operation,
                key,
                requestHash,
                Instant.now(),
                expiresAt
            );
            return true;
        }

        @Override
        public Optional<ApiIdempotencyRecord> lock(Long consumerId, String operation, String key) {
            return Optional.ofNullable(record);
        }

        @Override
        public void complete(
            Long consumerId,
            String operation,
            String key,
            int httpStatus,
            String responseJson,
            Long resourceId
        ) {
            record = record.completed(httpStatus, responseJson, resourceId, Instant.now());
        }
    }
}
