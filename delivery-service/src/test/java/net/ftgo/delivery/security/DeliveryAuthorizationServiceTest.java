package net.ftgo.delivery.security;

import net.ftgo.common.Address;
import net.ftgo.common.security.FtgoJwtAuthenticationConverter;
import net.ftgo.delivery.domain.Delivery;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DeliveryAuthorizationServiceTest {

    private final DeliveryAuthorizationService authorizationService =
        new DeliveryAuthorizationService();

    @Test
    void derivesCourierIdentityFromVerifiedPrincipal() {
        assertEquals(77L, authorizationService.requireCourierId(courierAuthentication(77L)));
    }

    @Test
    void rejectsCourierRoleWithoutCourierIdentity() {
        assertThrows(
            AccessDeniedException.class,
            () -> authorizationService.requireCourierId(authentication("COURIER", null))
        );
    }

    @Test
    void permitsAssignedCourierForPersistedDelivery() {
        Delivery delivery = assignedDelivery(77L);
        assertDoesNotThrow(() -> authorizationService.requireDeliveryAccess(
            delivery,
            courierAuthentication(77L)
        ));
    }

    @Test
    void rejectsDifferentCourierForPersistedDelivery() {
        Delivery delivery = assignedDelivery(77L);
        assertThrows(AccessDeniedException.class, () ->
            authorizationService.requireDeliveryAccess(
                delivery,
                courierAuthentication(88L)
            )
        );
    }

    @Test
    void permitsAdminForPersistedDelivery() {
        assertDoesNotThrow(() -> authorizationService.requireDeliveryAccess(
            assignedDelivery(77L),
            authentication("ADMIN", null)
        ));
    }

    private Delivery assignedDelivery(Long courierId) {
        Delivery delivery = new Delivery(
            123L,
            new Address("1 Pickup St", "Austin", "TX", "78701"),
            new Address("2 Dropoff St", "Austin", "TX", "78702"),
            LocalDateTime.now().plusHours(1)
        );
        delivery.assignCourier(courierId);
        return delivery;
    }

    private AbstractAuthenticationToken courierAuthentication(Long courierId) {
        return authentication("COURIER", courierId);
    }

    private AbstractAuthenticationToken authentication(String role, Long courierId) {
        Instant now = Instant.parse("2026-07-27T00:00:00Z");
        Jwt.Builder builder = Jwt.withTokenValue(role.toLowerCase() + "-token")
            .header("alg", "RS256")
            .subject(role.toLowerCase() + "-user")
            .issuedAt(now)
            .expiresAt(now.plusSeconds(300))
            .audience(List.of("ftgo-api"))
            .claim("roles", List.of(role));
        if (courierId != null) {
            builder.claim("courier_id", courierId);
        }
        return new FtgoJwtAuthenticationConverter("").convert(builder.build());
    }
}
