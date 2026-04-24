package net.ftgo.gateway.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Converts JWT tokens to Spring Security Authentication objects.
 * Extracts user ID and roles from JWT claims.
 */
public class JwtAuthenticationConverter implements Converter<Jwt, Mono<AbstractAuthenticationToken>> {
    
    private static final String ROLES_CLAIM = "roles";
    private static final String AUTHORITIES_CLAIM = "authorities";
    private static final String SCOPE_CLAIM = "scope";
    private static final String USER_ID_CLAIM = "sub";
    
    @Override
    public Mono<AbstractAuthenticationToken> convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = extractAuthorities(jwt);
        String userId = extractUserId(jwt);
        
        return Mono.just(new JwtAuthenticationToken(jwt, authorities, userId));
    }
    
    /**
     * Extract user ID from JWT token.
     * Uses 'sub' (subject) claim as the user identifier.
     */
    private String extractUserId(Jwt jwt) {
        return jwt.getClaimAsString(USER_ID_CLAIM);
    }
    
    /**
     * Extract authorities/roles from JWT token.
     * Supports multiple claim formats:
     * - "roles": ["ROLE_CONSUMER", "ROLE_ADMIN"]
     * - "authorities": ["ROLE_CONSUMER", "ROLE_ADMIN"]
     * - "scope": "ROLE_CONSUMER ROLE_ADMIN"
     */
    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        // Try "roles" claim first
        List<String> roles = jwt.getClaimAsStringList(ROLES_CLAIM);
        if (roles != null && !roles.isEmpty()) {
            return roles.stream()
                .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
        }
        
        // Try "authorities" claim
        List<String> authorities = jwt.getClaimAsStringList(AUTHORITIES_CLAIM);
        if (authorities != null && !authorities.isEmpty()) {
            return authorities.stream()
                .map(authority -> authority.startsWith("ROLE_") ? authority : "ROLE_" + authority)
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
        }
        
        // Try "scope" claim (space-separated string)
        String scope = jwt.getClaimAsString(SCOPE_CLAIM);
        if (scope != null && !scope.isEmpty()) {
            return List.of(scope.split(" ")).stream()
                .map(s -> s.startsWith("ROLE_") ? s : "ROLE_" + s)
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
        }
        
        return Collections.emptyList();
    }
}
