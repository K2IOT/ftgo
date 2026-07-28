package net.ftgo.kitchen.security;

import net.ftgo.common.security.FtgoJwtAuthenticationConverter;
import net.ftgo.kitchen.domain.Ticket;
import net.ftgo.kitchen.domain.TicketLineItem;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TicketAuthorizationServiceTest {

    private final TicketAuthorizationService authorizationService =
        new TicketAuthorizationService();

    @Test
    void permitsAssignedRestaurantForPersistedTicket() {
        Ticket ticket = ticketForRestaurant(20L);
        assertDoesNotThrow(() -> authorizationService.requireTicketAccess(
            ticket,
            restaurantAuthentication(List.of(20L))
        ));
    }

    @Test
    void rejectsRestaurantThatDoesNotOwnPersistedTicket() {
        Ticket ticket = ticketForRestaurant(20L);
        assertThrows(AccessDeniedException.class, () ->
            authorizationService.requireTicketAccess(
                ticket,
                restaurantAuthentication(List.of(10L))
            )
        );
    }

    @Test
    void permitsAdminForAnyPersistedTicket() {
        assertDoesNotThrow(() -> authorizationService.requireTicketAccess(
            ticketForRestaurant(20L),
            adminAuthentication()
        ));
    }

    @Test
    void queryScopeUsesRestaurantClaim() {
        assertDoesNotThrow(() -> authorizationService.requireRestaurantAccess(
            20L,
            restaurantAuthentication(List.of(20L, 21L))
        ));
        assertThrows(AccessDeniedException.class, () ->
            authorizationService.requireRestaurantAccess(
                99L,
                restaurantAuthentication(List.of(20L, 21L))
            )
        );
    }

    private Ticket ticketForRestaurant(Long restaurantId) {
        return new Ticket(
            restaurantId,
            123L,
            List.of(new TicketLineItem(5L, "Burger", 1))
        );
    }

    private AbstractAuthenticationToken restaurantAuthentication(List<Long> restaurantIds) {
        return authentication("restaurant-user", "RESTAURANT", restaurantIds);
    }

    private AbstractAuthenticationToken adminAuthentication() {
        return authentication("admin-user", "ADMIN", List.of());
    }

    private AbstractAuthenticationToken authentication(
        String subject,
        String role,
        List<Long> restaurantIds
    ) {
        Instant now = Instant.parse("2026-07-27T00:00:00Z");
        Jwt jwt = Jwt.withTokenValue(subject + "-token")
            .header("alg", "RS256")
            .subject(subject)
            .issuedAt(now)
            .expiresAt(now.plusSeconds(300))
            .audience(List.of("ftgo-api"))
            .claim("roles", List.of(role))
            .claim("restaurant_ids", restaurantIds)
            .build();
        return new FtgoJwtAuthenticationConverter("").convert(jwt);
    }
}
