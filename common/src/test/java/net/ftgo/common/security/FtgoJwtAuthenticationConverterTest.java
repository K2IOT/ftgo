package net.ftgo.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.lang.reflect.InvocationTargetException;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class FtgoJwtAuthenticationConverterTest {

    @Test
    void mapsConsumerIdentityAndRole() {
        AbstractAuthenticationToken authentication = convert(jwt(
            "consumer-user",
            List.of("ftgo-api"),
            Map.of(
                "consumer_id", 101L,
                "roles", List.of("CONSUMER")
            )
        ));

        Object principal = authentication.getPrincipal();
        assertEquals("consumer-user", principalValue(principal, "subject"));
        assertEquals(101L, principalValue(principal, "consumerId"));
        assertEquals(Set.of(), principalValue(principal, "restaurantIds"));
        assertNull(principalValue(principal, "courierId"));
        assertEquals(Set.of("CONSUMER"), principalValue(principal, "roles"));
        assertEquals(Set.of("ftgo-api"), principalValue(principal, "audiences"));
        assertAuthority(authentication, "ROLE_CONSUMER");
    }

    @Test
    void mapsRestaurantMembershipFromKeycloakRealmRoles() {
        AbstractAuthenticationToken authentication = convert(jwt(
            "restaurant-user",
            List.of("ftgo-api"),
            Map.of(
                "restaurant_ids", List.of("10", 20L),
                "realm_access", Map.of("roles", List.of("restaurant"))
            )
        ));

        Object principal = authentication.getPrincipal();
        assertEquals(Set.of(10L, 20L), principalValue(principal, "restaurantIds"));
        assertEquals(Set.of("RESTAURANT"), principalValue(principal, "roles"));
        assertAuthority(authentication, "ROLE_RESTAURANT");
    }

    @Test
    void mapsCourierIdentityAndLegacyAuthoritiesClaim() {
        AbstractAuthenticationToken authentication = convert(jwt(
            "courier-user",
            List.of("ftgo-api"),
            Map.of(
                "courier_id", "77",
                "authorities", List.of("ROLE_COURIER")
            )
        ));

        Object principal = authentication.getPrincipal();
        assertEquals(77L, principalValue(principal, "courierId"));
        assertEquals(Set.of("COURIER"), principalValue(principal, "roles"));
        assertAuthority(authentication, "ROLE_COURIER");
    }

    @Test
    void mapsAdminRoleFromScopeClaim() {
        AbstractAuthenticationToken authentication = convert(jwt(
            "admin-user",
            List.of("ftgo-api", "ftgo-internal"),
            Map.of("scope", "ADMIN")
        ));

        Object principal = authentication.getPrincipal();
        assertEquals(Set.of("ADMIN"), principalValue(principal, "roles"));
        assertEquals(Set.of("ftgo-api", "ftgo-internal"), principalValue(principal, "audiences"));
        assertAuthority(authentication, "ROLE_ADMIN");
    }

    @Test
    void rejectsMalformedNumericIdentityClaim() {
        BadJwtException error = assertThrows(BadJwtException.class, () -> convert(jwt(
            "consumer-user",
            List.of("ftgo-api"),
            Map.of(
                "consumer_id", "not-a-number",
                "roles", List.of("CONSUMER")
            )
        )));

        assertTrue(error.getMessage().contains("consumer_id"));
    }

    @Test
    void rejectsTokenWithoutRequiredAudience() {
        BadJwtException error = assertThrows(BadJwtException.class, () -> convert(jwt(
            "consumer-user",
            List.of("another-api"),
            Map.of(
                "consumer_id", 101L,
                "roles", List.of("CONSUMER")
            )
        )));

        assertTrue(error.getMessage().contains("ftgo-api"));
    }

    private AbstractAuthenticationToken convert(Jwt jwt) {
        try {
            Class<?> converterType = Class.forName("net.ftgo.common.security.FtgoJwtAuthenticationConverter");
            Object converter = converterType.getConstructor(String.class).newInstance("ftgo-api");
            Object result = converterType.getMethod("convert", Jwt.class).invoke(converter, jwt);
            return assertInstanceOf(AbstractAuthenticationToken.class, result);
        } catch (ClassNotFoundException error) {
            return fail("FtgoJwtAuthenticationConverter is not implemented", error);
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            return fail("Converter threw a checked exception", cause);
        } catch (ReflectiveOperationException error) {
            return fail("FtgoJwtAuthenticationConverter does not expose the required API", error);
        }
    }

    private Jwt jwt(String subject, List<String> audiences, Map<String, Object> claims) {
        Instant issuedAt = Instant.parse("2026-07-27T00:00:00Z");
        Jwt.Builder builder = Jwt.withTokenValue("test-token")
            .header("alg", "RS256")
            .subject(subject)
            .issuedAt(issuedAt)
            .expiresAt(issuedAt.plusSeconds(300))
            .audience(audiences);
        claims.forEach(builder::claim);
        return builder.build();
    }

    private Object principalValue(Object principal, String accessor) {
        try {
            return principal.getClass().getMethod(accessor).invoke(principal);
        } catch (ReflectiveOperationException error) {
            return fail("Principal does not expose accessor " + accessor, error);
        }
    }

    private void assertAuthority(AbstractAuthenticationToken authentication, String expected) {
        Collection<String> authorities = authentication.getAuthorities().stream()
            .map(authority -> authority.getAuthority())
            .toList();
        assertTrue(authorities.contains(expected), () -> "Missing authority " + expected + " in " + authorities);
    }
}
