package net.ftgo.accounting.messaging;

import java.time.LocalDateTime;

public record AuthorizationVoidedEvent(Long accountId, Long orderId, Long authorizationId,
                                       String reason, String requestId, LocalDateTime occurredAt) {
}
