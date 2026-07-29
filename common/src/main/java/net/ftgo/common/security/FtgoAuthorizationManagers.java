package net.ftgo.common.security;

import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** Audience-aware authorization policies shared by servlet resource servers. */
public final class FtgoAuthorizationManagers {

    private static final String ROLE_PREFIX = "ROLE_";
    private static final String AUDIENCE_PREFIX = "AUD_";

    private FtgoAuthorizationManagers() {
    }

    public static AuthorizationManager<RequestAuthorizationContext> publicApi(
        String publicAudience,
        String... roles
    ) {
        String requiredAudience = requireValue(publicAudience, "publicAudience");
        Set<String> requiredRoles = Arrays.stream(roles)
            .map(role -> requireValue(role, "role").toUpperCase(Locale.ROOT))
            .collect(Collectors.toUnmodifiableSet());
        if (requiredRoles.isEmpty()) {
            throw new IllegalArgumentException("At least one application role is required");
        }

        return (authentication, context) -> decide(
            authentication,
            requiredRoles,
            requiredAudience
        );
    }

    public static AuthorizationManager<RequestAuthorizationContext> internalService(
        String internalAudience
    ) {
        return (authentication, context) -> decide(
            authentication,
            Set.of("SERVICE"),
            requireValue(internalAudience, "internalAudience")
        );
    }

    private static AuthorizationDecision decide(
        Supplier<Authentication> authenticationSupplier,
        Set<String> requiredRoles,
        String requiredAudience
    ) {
        Authentication authentication = authenticationSupplier.get();
        if (authentication == null || !authentication.isAuthenticated()) {
            return new AuthorizationDecision(false);
        }

        Set<String> authorities = authentication.getAuthorities().stream()
            .map(authority -> authority.getAuthority())
            .collect(Collectors.toUnmodifiableSet());
        boolean roleAllowed = requiredRoles.stream()
            .map(role -> ROLE_PREFIX + role)
            .anyMatch(authorities::contains);
        boolean audienceAllowed = authorities.contains(AUDIENCE_PREFIX + requiredAudience);
        return new AuthorizationDecision(roleAllowed && audienceAllowed);
    }

    private static String requireValue(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
