package net.ftgo.common.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.Objects;

/**
 * Authenticated FTGO identity backed by the verified JWT.
 *
 * The raw JWT is exposed only through the explicit {@link #tokenValue()} method
 * for trusted internal token relay. Credentials remain empty so generic
 * authentication logging and serialization do not expose the bearer token.
 */
public final class FtgoJwtAuthenticationToken extends AbstractAuthenticationToken {

    private final FtgoPrincipal principal;
    private final Jwt jwt;

    public FtgoJwtAuthenticationToken(
        FtgoPrincipal principal,
        Jwt jwt,
        Collection<? extends GrantedAuthority> authorities
    ) {
        super(authorities);
        this.principal = Objects.requireNonNull(principal, "principal is required");
        this.jwt = Objects.requireNonNull(jwt, "jwt is required");
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

    public String tokenValue() {
        return jwt.getTokenValue();
    }

    public Jwt jwt() {
        return jwt;
    }
}
