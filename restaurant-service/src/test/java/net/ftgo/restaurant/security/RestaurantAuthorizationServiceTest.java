package net.ftgo.restaurant.security;

import net.ftgo.common.security.FtgoJwtAuthenticationConverter;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RestaurantAuthorizationServiceTest {

    private final RestaurantAuthorizationService authorizationService =
        new RestaurantAuthorizationService();

    @Test
    void permitsAssignedRestaurant() {
        assertDoesNotThrow(() -> authorizationService.requireRestaurantAccess(
            10L,
            restaurantAuthentication(List.of(10L, 11L))
        ));
    }

    @Test
    void rejectsCrossRestaurantAccess() {
        assertThrows(AccessDeniedException.class, () ->
            authorizationService.requireRestaurantAccess(
                20L,
                restaurantAuthentication(List.of(10L, 11L))
            )
        );
    }

    @Test
    void permitsAdminForAnyRestaurant() {
        assertDoesNotThrow(() -> authorizationService.requireRestaurantAccess(
            20L,
            adminAuthentication()
        ));
    }

    @Test
    void restaurantPrincipalCannotCreateRestaurant() {
        assertThrows(AccessDeniedException.class, () ->
            authorizationService.requireAdmin(restaurantAuthentication(List.of(10L)))
        );
        assertDoesNotThrow(() -> authorizationService.requireAdmin(adminAuthentication()));
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
