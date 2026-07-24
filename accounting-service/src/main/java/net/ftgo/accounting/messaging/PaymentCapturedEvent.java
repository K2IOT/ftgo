package net.ftgo.accounting.messaging;

import java.time.LocalDateTime;

public record PaymentCapturedEvent(Long accountId, Long orderId, Long authorizationId,
                                   String requestId, LocalDateTime occurredAt) {
}
