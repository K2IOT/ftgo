package net.ftgo.delivery.service;

import net.ftgo.common.Address;
import net.ftgo.common.security.FtgoJwtAuthenticationConverter;
import net.ftgo.delivery.domain.Delivery;
import net.ftgo.delivery.messaging.DomainEventPublisher;
import net.ftgo.delivery.repository.DeliveryRepository;
import net.ftgo.delivery.security.DeliveryAuthorizationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false"
})
@Import({DeliveryService.class, DeliveryAuthorizationService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DeliveryClaimConcurrencyTest {

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Autowired
    private DeliveryService deliveryService;

    @MockBean
    private DomainEventPublisher eventPublisher;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @AfterEach
    void tearDown() throws Exception {
        executor.shutdownNow();
        executor.awaitTermination(5, TimeUnit.SECONDS);
        deliveryRepository.deleteAll();
    }

    @Test
    void exactlyOneCourierWinsConcurrentClaim() throws Exception {
        Delivery delivery = deliveryRepository.saveAndFlush(new Delivery(
            123L,
            new Address("1 Pickup St", "Austin", "TX", "78701"),
            new Address("2 Dropoff St", "Austin", "TX", "78702"),
            LocalDateTime.now().plusHours(1)
        ));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<ClaimResult> first = executor.submit(() ->
            claimAfterBarrier(delivery.getId(), 77L, ready, start)
        );
        Future<ClaimResult> second = executor.submit(() ->
            claimAfterBarrier(delivery.getId(), 88L, ready, start)
        );

        assertTrue(ready.await(5, TimeUnit.SECONDS), "Both claim attempts must be ready");
        start.countDown();

        List<ClaimResult> results = List.of(
            first.get(10, TimeUnit.SECONDS),
            second.get(10, TimeUnit.SECONDS)
        );

        long winners = results.stream().filter(ClaimResult::won).count();
        long conflicts = results.stream().filter(result -> result.error() instanceof IllegalStateException).count();
        assertEquals(1L, winners);
        assertEquals(1L, conflicts);

        Delivery persisted = deliveryRepository.findById(delivery.getId()).orElseThrow();
        assertTrue(
            persisted.getCourierId().equals(77L) || persisted.getCourierId().equals(88L),
            "Persisted courier must be one of the authenticated claimants"
        );
    }

    private ClaimResult claimAfterBarrier(
        Long deliveryId,
        Long courierId,
        CountDownLatch ready,
        CountDownLatch start
    ) {
        ready.countDown();
        try {
            if (!start.await(5, TimeUnit.SECONDS)) {
                return new ClaimResult(false, new IllegalStateException("Claim start timed out"));
            }
            deliveryService.claimDelivery(deliveryId, courierAuthentication(courierId));
            return new ClaimResult(true, null);
        } catch (Throwable error) {
            return new ClaimResult(false, rootCause(error));
        }
    }

    private Throwable rootCause(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private AbstractAuthenticationToken courierAuthentication(Long courierId) {
        Instant now = Instant.parse("2026-07-27T00:00:00Z");
        Jwt jwt = Jwt.withTokenValue("courier-" + courierId + "-token")
            .header("alg", "RS256")
            .subject("courier-" + courierId)
            .issuedAt(now)
            .expiresAt(now.plusSeconds(300))
            .audience(List.of("ftgo-api"))
            .claim("roles", List.of("COURIER"))
            .claim("courier_id", courierId)
            .build();
        return new FtgoJwtAuthenticationConverter("").convert(jwt);
    }

    private record ClaimResult(boolean won, Throwable error) {
    }
}
