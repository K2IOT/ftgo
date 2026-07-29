package net.ftgo.orderhistory.security;

import net.ftgo.common.security.FtgoPrincipal;
import net.ftgo.orderhistory.domain.OrderHistoryRecord;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderHistoryAuthorizationServiceTest {

    private final OrderHistoryAuthorizationService authorizationService =
        new OrderHistoryAuthorizationService();

    @Test
    void permitsConsumerToReadOwnHistory() {
        assertDoesNotThrow(() -> authorizationService.requireConsumerAccess(
            101L,
            consumer(101L)
        ));
    }

    @Test
    void rejectsConsumerReadingAnotherConsumersHistory() {
        assertThrows(
            AccessDeniedException.class,
            () -> authorizationService.requireConsumerAccess(202L, consumer(101L))
        );
    }

    @Test
    void rejectsConsumerTokenWithoutConsumerIdentity() {
        FtgoPrincipal missingIdentity = new FtgoPrincipal(
            "consumer-without-id",
            null,
            Set.of(),
            null,
            Set.of("CONSUMER"),
            Set.of("ftgo-api")
        );

        assertThrows(
            AccessDeniedException.class,
            () -> authorizationService.requireConsumerAccess(101L, missingIdentity)
        );
    }

    @Test
    void permitsAdminToReadAnyConsumersHistory() {
        assertDoesNotThrow(() -> authorizationService.requireConsumerAccess(
            202L,
            admin()
        ));
    }

    @Test
    void rejectsConsumerReadingOrderOwnedByAnotherConsumer() {
        OrderHistoryRecord record = order("9001", 202L);

        assertThrows(
            AccessDeniedException.class,
            () -> authorizationService.requireOrderAccess(record, consumer(101L))
        );
    }

    @Test
    void permitsConsumerToReadOwnedOrder() {
        OrderHistoryRecord record = order("9001", 101L);

        assertDoesNotThrow(() -> authorizationService.requireOrderAccess(
            record,
            consumer(101L)
        ));
    }

    private OrderHistoryRecord order(String orderId, Long consumerId) {
        OrderHistoryRecord record = new OrderHistoryRecord(orderId);
        record.setConsumerId(consumerId);
        return record;
    }

    private FtgoPrincipal consumer(Long consumerId) {
        return new FtgoPrincipal(
            "consumer-" + consumerId,
            consumerId,
            Set.of(),
            null,
            Set.of("CONSUMER"),
            Set.of("ftgo-api")
        );
    }

    private FtgoPrincipal admin() {
        return new FtgoPrincipal(
            "admin-user",
            null,
            Set.of(),
            null,
            Set.of("ADMIN"),
            Set.of("ftgo-api")
        );
    }
}
