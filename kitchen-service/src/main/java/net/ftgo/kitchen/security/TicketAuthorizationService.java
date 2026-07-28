package net.ftgo.kitchen.security;

import net.ftgo.common.security.FtgoPrincipal;
import net.ftgo.common.security.PrincipalAccess;
import net.ftgo.kitchen.domain.Ticket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.Objects;

/** Enforces restaurant ownership using the restaurant stored on the ticket. */
@Service
public class TicketAuthorizationService {

    private static final Logger logger =
        LoggerFactory.getLogger(TicketAuthorizationService.class);

    public void requireTicketAccess(Ticket ticket, Authentication authentication) {
        Objects.requireNonNull(ticket, "ticket is required");
        requireRestaurantAccess(ticket.getRestaurantId(), authentication);
    }

    public void requireRestaurantAccess(Long restaurantId, Authentication authentication) {
        if (restaurantId == null) {
            throw new AccessDeniedException("Ticket access denied");
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
            "Denied ticket access: subject={}, restaurantId={}",
            principal.subject(),
            restaurantId
        );
        throw new AccessDeniedException("Ticket access denied");
    }
}
