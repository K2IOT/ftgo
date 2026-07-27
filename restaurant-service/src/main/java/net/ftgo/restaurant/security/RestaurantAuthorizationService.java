package net.ftgo.restaurant.security;

import net.ftgo.common.security.FtgoPrincipal;
import net.ftgo.common.security.PrincipalAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/** Enforces restaurant-resource ownership from the verified FTGO principal. */
@Service
public class RestaurantAuthorizationService {

    private static final Logger logger =
        LoggerFactory.getLogger(RestaurantAuthorizationService.class);

    public void requireRestaurantAccess(Long restaurantId, Authentication authentication) {
        if (restaurantId == null) {
            throw new AccessDeniedException("Restaurant access denied");
        }

        FtgoPrincipal principal = PrincipalAccess.require(authentication);
        if (principal.roles().contains("ADMIN")) {
            return;
        }
        if (principal.roles().contains("RESTAURANT")
            && principal.restaurantIds().contains(restaurantId)) {
            return;
        }

        logger.warn(
            "Denied restaurant access: subject={}, restaurantId={}",
            principal.subject(),
            restaurantId
        );
        throw new AccessDeniedException("Restaurant access denied");
    }

    public void requireAdmin(Authentication authentication) {
        FtgoPrincipal principal = PrincipalAccess.require(authentication);
        if (principal.roles().contains("ADMIN")) {
            return;
        }

        logger.warn("Denied restaurant administration: subject={}", principal.subject());
        throw new AccessDeniedException("Restaurant administration requires ADMIN role");
    }
}
