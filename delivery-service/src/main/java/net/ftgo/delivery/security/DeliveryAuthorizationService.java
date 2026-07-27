package net.ftgo.delivery.security;

import net.ftgo.common.security.FtgoPrincipal;
import net.ftgo.common.security.PrincipalAccess;
import net.ftgo.delivery.domain.Delivery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.Objects;

/** Enforces courier identity and persisted delivery ownership. */
@Service
public class DeliveryAuthorizationService {

    private static final Logger logger =
        LoggerFactory.getLogger(DeliveryAuthorizationService.class);

    public Long requireCourierId(Authentication authentication) {
        FtgoPrincipal principal = PrincipalAccess.require(authentication);
        if (!principal.roles().contains("COURIER") || principal.courierId() == null) {
            logger.warn("Denied courier operation: subject={}", principal.subject());
            throw new AccessDeniedException("Courier identity is required");
        }
        return principal.courierId();
    }

    public void requireDeliveryAccess(Delivery delivery, Authentication authentication) {
        Objects.requireNonNull(delivery, "delivery is required");
        FtgoPrincipal principal = PrincipalAccess.require(authentication);
        if (principal.roles().contains("ADMIN")) {
            return;
        }
        if (principal.roles().contains("COURIER")
            && principal.courierId() != null
            && principal.courierId().equals(delivery.getCourierId())) {
            return;
        }

        logger.warn(
            "Denied delivery access: subject={}, deliveryId={}, assignedCourierId={}",
            principal.subject(),
            delivery.getId(),
            delivery.getCourierId()
        );
        throw new AccessDeniedException("Delivery access denied");
    }
}
