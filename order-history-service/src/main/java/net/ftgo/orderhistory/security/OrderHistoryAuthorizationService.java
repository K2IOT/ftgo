package net.ftgo.orderhistory.security;

import net.ftgo.common.security.FtgoPrincipal;
import net.ftgo.orderhistory.domain.OrderHistoryRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class OrderHistoryAuthorizationService {

    private static final Logger log = LoggerFactory.getLogger(OrderHistoryAuthorizationService.class);

    public void requireConsumerAccess(Long consumerId, FtgoPrincipal principal) {
        Objects.requireNonNull(principal, "principal is required");

        if (consumerId == null) {
            log.warn(
                "Order history access denied actorSubject={} actorConsumerId={} reason=missing-owner",
                principal.subject(),
                principal.consumerId()
            );
            throw new AccessDeniedException("Order history access denied");
        }

        if (principal.roles().contains("ADMIN")) {
            return;
        }

        if (principal.consumerId() == null || !consumerId.equals(principal.consumerId())) {
            log.warn(
                "Order history access denied actorSubject={} actorConsumerId={} consumerId={}",
                principal.subject(),
                principal.consumerId(),
                consumerId
            );
            throw new AccessDeniedException("Order history access denied");
        }
    }

    public void requireOrderAccess(OrderHistoryRecord record, FtgoPrincipal principal) {
        Objects.requireNonNull(record, "record is required");
        requireConsumerAccess(record.getConsumerId(), principal);
    }
}
