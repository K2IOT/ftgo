package net.ftgo.common.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class FtgoJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final String ROLE_PREFIX = "ROLE_";

    private final String requiredAudience;

    public FtgoJwtAuthenticationConverter() {
        this("ftgo-api");
    }

    public FtgoJwtAuthenticationConverter(String requiredAudience) {
        this.requiredAudience = requiredAudience == null ? "" : requiredAudience.trim();
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Objects.requireNonNull(jwt, "jwt is required");

        Set<String> audiences = normalizeAudiences(jwt.getAudience());
        if (!requiredAudience.isEmpty() && !audiences.contains(requiredAudience)) {
            throw new BadJwtException("JWT audience must contain " + requiredAudience);
        }

        String subject = jwt.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new BadJwtException("JWT subject is required");
        }

        Set<String> roles = extractRoles(jwt);
        FtgoPrincipal principal = new FtgoPrincipal(
            subject,
            optionalPositiveLong(jwt.getClaim("consumer_id"), "consumer_id"),
            positiveLongSet(jwt.getClaim("restaurant_ids"), "restaurant_ids"),
            optionalPositiveLong(jwt.getClaim("courier_id"), "courier_id"),
            roles,
            audiences
        );

        List<GrantedAuthority> authorities = roles.stream()
            .sorted()
            .map(role -> new SimpleGrantedAuthority(ROLE_PREFIX + role))
            .map(GrantedAuthority.class::cast)
            .toList();

        return new FtgoAuthenticationToken(principal, authorities);
    }

    private Set<String> normalizeAudiences(Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                result.add(value.trim());
            }
        }
        return Set.copyOf(result);
    }

    private Set<String> extractRoles(Jwt jwt) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        addRoleValues(result, jwt.getClaim("roles"), "roles");
        addRoleValues(result, jwt.getClaim("authorities"), "authorities");
        addScopeRoles(result, jwt.getClaim("scope"));

        Object realmAccessValue = jwt.getClaim("realm_access");
        if (realmAccessValue != null) {
            if (!(realmAccessValue instanceof Map<?, ?> realmAccess)) {
                throw new BadJwtException("Invalid realm_access claim");
            }
            addRoleValues(result, realmAccess.get("roles"), "realm_access.roles");
        }

        return Set.copyOf(result);
    }

    private void addScopeRoles(Set<String> target, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String scope) {
            for (String role : scope.trim().split("\\s+")) {
                addRole(target, role, "scope");
            }
            return;
        }
        addRoleValues(target, value, "scope");
    }

    private void addRoleValues(Set<String> target, Object value, String claimName) {
        if (value == null) {
            return;
        }
        if (value instanceof String role) {
            addRole(target, role, claimName);
            return;
        }
        if (!(value instanceof Collection<?> values)) {
            throw new BadJwtException("Invalid " + claimName + " claim");
        }
        for (Object entry : values) {
            if (!(entry instanceof String role)) {
                throw new BadJwtException("Invalid " + claimName + " claim");
            }
            addRole(target, role, claimName);
        }
    }

    private void addRole(Set<String> target, String rawRole, String claimName) {
        String role = rawRole == null ? "" : rawRole.trim();
        if (role.isEmpty()) {
            return;
        }
        if (role.regionMatches(true, 0, ROLE_PREFIX, 0, ROLE_PREFIX.length())) {
            role = role.substring(ROLE_PREFIX.length());
        }
        role = role.trim().toUpperCase(Locale.ROOT);
        if (role.isEmpty()) {
            throw new BadJwtException("Invalid " + claimName + " claim");
        }
        target.add(role);
    }

    private Long optionalPositiveLong(Object value, String claimName) {
        if (value == null) {
            return null;
        }
        return positiveLong(value, claimName);
    }

    private Set<Long> positiveLongSet(Object value, String claimName) {
        if (value == null) {
            return Set.of();
        }
        if (!(value instanceof Collection<?> values)) {
            throw new BadJwtException("Invalid " + claimName + " claim");
        }
        LinkedHashSet<Long> result = new LinkedHashSet<>();
        for (Object entry : values) {
            result.add(positiveLong(entry, claimName));
        }
        return Set.copyOf(result);
    }

    private Long positiveLong(Object value, String claimName) {
        try {
            long parsed;
            if (value instanceof Number number) {
                parsed = new BigDecimal(number.toString()).longValueExact();
            } else if (value instanceof String stringValue) {
                parsed = Long.parseLong(stringValue.trim());
            } else {
                throw new NumberFormatException("unsupported claim type");
            }
            if (parsed <= 0) {
                throw new NumberFormatException("identity must be positive");
            }
            return parsed;
        } catch (ArithmeticException | NumberFormatException error) {
            throw new BadJwtException("Invalid " + claimName + " claim", error);
        }
    }

    private static final class FtgoAuthenticationToken extends AbstractAuthenticationToken {

        private final FtgoPrincipal principal;

        private FtgoAuthenticationToken(
            FtgoPrincipal principal,
            Collection<? extends GrantedAuthority> authorities
        ) {
            super(authorities);
            this.principal = principal;
            setAuthenticated(true);
        }

        @Override
        public Object getCredentials() {
            return "";
        }

        @Override
        public FtgoPrincipal getPrincipal() {
            return principal;
        }

        @Override
        public String getName() {
            return principal.subject();
        }
    }
}
