package net.ftgo.common.security;

import java.util.Locale;
import java.util.Set;

/** Central allowlist for application roles accepted from identity-provider claims. */
public final class FtgoRoles {

    private static final Set<String> KNOWN_ROLES = Set.of(
        "CONSUMER",
        "RESTAURANT",
        "COURIER",
        "ADMIN",
        "SERVICE"
    );

    private FtgoRoles() {
    }

    public static boolean isKnown(String role) {
        if (role == null || role.isBlank()) {
            return false;
        }
        return KNOWN_ROLES.contains(role.trim().toUpperCase(Locale.ROOT));
    }
}
