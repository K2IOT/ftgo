package net.ftgo.consumer.security;

import net.ftgo.common.security.FtgoPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class ConsumerAuthorizationService {

    private static final Logger log = LoggerFactory.getLogger(ConsumerAuthorizationService.class);

    public void requireConsumerAccess(Long consumerId, FtgoPrincipal principal) {
        Objects.requireNonNull(consumerId, "consumerId is required");
        Objects.requireNonNull(principal, "principal is required");

        if (principal.roles().contains("ADMIN")) {
            log.info(
                "Admin consumer profile access granted actorSubject={} consumerId={}",
                principal.subject(),
                consumerId
            );
            return;
        }

        if (principal.consumerId() == null || !consumerId.equals(principal.consumerId())) {
            log.warn(
                "Consumer profile access denied actorSubject={} actorConsumerId={} consumerId={}",
                principal.subject(),
                principal.consumerId(),
                consumerId
            );
            throw new AccessDeniedException("Consumer access denied");
        }
    }

    public void requireAdmin(FtgoPrincipal principal) {
        Objects.requireNonNull(principal, "principal is required");
        if (!principal.roles().contains("ADMIN")) {
            log.warn(
                "Admin consumer operation denied actorSubject={} roles={}",
                principal.subject(),
                principal.roles()
            );
            throw new AccessDeniedException("Admin access required");
        }
    }
}
