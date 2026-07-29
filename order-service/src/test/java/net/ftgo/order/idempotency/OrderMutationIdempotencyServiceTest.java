package net.ftgo.order.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.ftgo.common.Address;
import net.ftgo.common.Money;
import net.ftgo.order.api.CreateOrderRequest;
import net.ftgo.order.api.OrderLineItemRequest;
import net.ftgo.order.api.ReviseOrderRequest;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderMutationIdempotencyServiceTest {

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
            "CREATE_ORDER",
            "create-101-1",
            hash,
            () -> {
                mutations.incrementAndGet();
                return "{\"orderId\":9001}";
            }
        );
        IdempotentResult<String> replay = service.execute(
            101L,
            "CREATE_ORDER",
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
        assertEquals(false, first.replayed());
        assertEquals(true, replay.replayed());
    }

    @Test
    void changedRequestHashIsRejectedBeforeMutation() {
        AtomicInteger mutations = new AtomicInteger();
        service.execute(
            101L,
            "CREATE_ORDER",
            "create-101-2",
            new byte[] {1},
            () -> "{\"orderId\":9001}"
        );

        assertThrows(IdempotencyKeyConflictException.class, () -> service.execute(
            101L,
            "CREATE_ORDER",
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
    void canonicalCreateHashExcludesPaymentTokenButIncludesBusinessPayload() {
        CreateOrderRequest first = createRequest("tok_secret_one", 1);
        CreateOrderRequest sameBusinessRequest = createRequest("tok_secret_two", 1);
        CreateOrderRequest changedQuantity = createRequest("tok_secret_two", 2);

        byte[] firstHash = service.hashCreate(101L, first);
        assertArrayEquals(firstHash, service.hashCreate(101L, sameBusinessRequest));
        org.junit.jupiter.api.Assertions.assertFalse(java.util.Arrays.equals(
            firstHash,
            service.hashCreate(101L, changedQuantity)
        ));
    }

    @Test
    void canonicalHashesBindCancelAndReviseToConsumerAndOrder() {
        ReviseOrderRequest revision = new ReviseOrderRequest(List.of(
            new OrderLineItemRequest(1L, "Pizza", new Money("20.00"), 2)
        ));

        org.junit.jupiter.api.Assertions.assertFalse(java.util.Arrays.equals(
            service.hashCancel(101L, 9001L),
            service.hashCancel(202L, 9001L)
        ));
        org.junit.jupiter.api.Assertions.assertFalse(java.util.Arrays.equals(
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
            LocalDateTime.of(2026, 7, 30, 12, 0),
            paymentToken
        );
    }

    private static final class InMemoryApiIdempotencyStore implements ApiIdempotencyStore {
        private ApiIdempotencyRecord record;

        @Override
        public boolean insertProcessing(
            Long consumerId,
            String operation,
            String key,
            byte[] requestHash,
            java.time.Instant expiresAt
        ) {
            if (record != null) {
                return false;
            }
            record = ApiIdempotencyRecord.processing(
                consumerId,
                operation,
                key,
                requestHash,
                java.time.Instant.now(),
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
            record = record.completed(httpStatus, responseJson, resourceId, java.time.Instant.now());
        }
    }
}
