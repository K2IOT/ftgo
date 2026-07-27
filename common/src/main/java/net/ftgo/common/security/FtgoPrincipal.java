package net.ftgo.common.security;

import java.util.Set;

public record FtgoPrincipal(
    String subject,
    Long consumerId,
    Set<Long> restaurantIds,
    Long courierId,
    Set<String> roles,
    Set<String> audiences
) {

    public FtgoPrincipal {
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("subject is required");
        }
        restaurantIds = restaurantIds == null ? Set.of() : Set.copyOf(restaurantIds);
        roles = roles == null ? Set.of() : Set.copyOf(roles);
        audiences = audiences == null ? Set.of() : Set.copyOf(audiences);
    }
}
