package net.ftgo.consumer.security;

import net.ftgo.common.security.FtgoPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConsumerAuthorizationServiceTest {

    private final ConsumerAuthorizationService authorizationService =
        new ConsumerAuthorizationService();

    @Test
    void permitsConsumerToAccessOwnProfile() {
        assertDoesNotThrow(() -> authorizationService.requireConsumerAccess(
            101L,
            consumer(101L)
        ));
    }

    @Test
    void rejectsConsumerAccessToAnotherProfile() {
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
    void permitsAdminToAccessAnyProfile() {
        assertDoesNotThrow(() -> authorizationService.requireConsumerAccess(
            202L,
            admin()
        ));
    }

    @Test
    void requiresAdminForCreditLimitMutation() {
        assertThrows(
            AccessDeniedException.class,
            () -> authorizationService.requireAdmin(consumer(101L))
        );
        assertDoesNotThrow(() -> authorizationService.requireAdmin(admin()));
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
