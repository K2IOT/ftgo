package net.ftgo.common.security;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;

public final class PrincipalAccess {

    private PrincipalAccess() {
    }

    public static FtgoPrincipal require(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AuthenticationCredentialsNotFoundException("Authenticated FTGO principal is required");
        }
        if (!(authentication.getPrincipal() instanceof FtgoPrincipal principal)) {
            throw new AuthenticationCredentialsNotFoundException("Authentication does not contain an FTGO principal");
        }
        return principal;
    }
}
