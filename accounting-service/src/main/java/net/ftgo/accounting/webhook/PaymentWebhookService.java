package net.ftgo.accounting.webhook;

import net.ftgo.accounting.domain.Authorization;
import net.ftgo.accounting.domain.AuthorizationStatus;
import net.ftgo.accounting.domain.FinancialOperationStatus;
import net.ftgo.accounting.domain.PaymentCapture;
import net.ftgo.accounting.domain.PaymentRefund;
import net.ftgo.accounting.repository.AuthorizationRepository;
import net.ftgo.common.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
public class PaymentWebhookService {

    private final AuthorizationRepository authorizationRepository;
    private final PaymentWebhookEventRepository eventRepository;

    public PaymentWebhookService(
        AuthorizationRepository authorizationRepository,
        PaymentWebhookEventRepository eventRepository
    ) {
        this.authorizationRepository = authorizationRepository;
        this.eventRepository = eventRepository;
    }

    @Transactional
    public PaymentWebhookApplyResult apply(
        String provider,
        PaymentWebhookPayload payload,
        String payloadHash
    ) {
        validate(provider, payload, payloadHash);
        if (eventRepository.existsByProviderAndProviderEventId(
            provider,
            payload.getEventId()
        )) {
            return PaymentWebhookApplyResult.DUPLICATE;
        }

        Authorization authorization = authorizationRepository
            .findByProviderAuthorizationIdForUpdate(payload.getProviderAuthorizationId())
            .orElse(null);
        PaymentWebhookApplyResult result;
        if (authorization == null) {
            result = PaymentWebhookApplyResult.UNKNOWN_AUTHORIZATION;
        } else {
            result = applyTransition(provider, authorization, payload);
            if (result == PaymentWebhookApplyResult.APPLIED) {
                authorizationRepository.saveAndFlush(authorization);
            }
        }

        eventRepository.saveAndFlush(new PaymentWebhookEvent(
            provider,
            payload,
            payloadHash,
            result
        ));
        return result;
    }

    private PaymentWebhookApplyResult applyTransition(
        String provider,
        Authorization authorization,
        PaymentWebhookPayload payload
    ) {
        return switch (payload.getType()) {
            case PAYMENT_CAPTURED -> applyCapture(provider, authorization, payload);
            case AUTHORIZATION_VOIDED -> applyVoid(provider, authorization, payload);
            case PAYMENT_REFUNDED -> applyRefund(provider, authorization, payload);
        };
    }

    private PaymentWebhookApplyResult applyCapture(
        String provider,
        Authorization authorization,
        PaymentWebhookPayload payload
    ) {
        requireText(payload.getProviderCaptureId(), "providerCaptureId");
        if (!sameAmount(authorization.getAmount(), payload.getAmount())) {
            return PaymentWebhookApplyResult.MANUAL_REVIEW_REQUIRED;
        }
        if (authorization.getStatus() == AuthorizationStatus.CAPTURED) {
            boolean sameCapture = authorization.getPaymentCaptures().stream()
                .filter(capture -> capture.getStatus() == FinancialOperationStatus.SUCCEEDED)
                .anyMatch(capture -> Objects.equals(
                    capture.getProviderCaptureId(),
                    payload.getProviderCaptureId()
                ));
            return sameCapture
                ? PaymentWebhookApplyResult.ALREADY_APPLIED
                : PaymentWebhookApplyResult.MANUAL_REVIEW_REQUIRED;
        }
        if (authorization.getStatus() != AuthorizationStatus.AUTHORIZED
            && authorization.getStatus() != AuthorizationStatus.APPROVED) {
            return PaymentWebhookApplyResult.IGNORED_STATE_REGRESSION;
        }

        PaymentCapture pending = authorization.getPaymentCaptures().stream()
            .filter(capture -> capture.getStatus() == FinancialOperationStatus.PENDING)
            .findFirst()
            .orElse(null);
        if (pending == null && !authorization.getPaymentCaptures().isEmpty()) {
            return PaymentWebhookApplyResult.MANUAL_REVIEW_REQUIRED;
        }
        String requestId;
        if (pending == null) {
            requestId = webhookRequestId(provider, payload.getEventId(), "capture");
            authorization.requestCapture(requestId);
        } else {
            requestId = pending.getRequestId();
        }
        authorization.completeCapture(requestId, payload.getProviderCaptureId());
        return PaymentWebhookApplyResult.APPLIED;
    }

    private PaymentWebhookApplyResult applyVoid(
        String provider,
        Authorization authorization,
        PaymentWebhookPayload payload
    ) {
        if (authorization.getStatus() == AuthorizationStatus.VOIDED) {
            return PaymentWebhookApplyResult.ALREADY_APPLIED;
        }
        if (authorization.getStatus() != AuthorizationStatus.AUTHORIZED
            && authorization.getStatus() != AuthorizationStatus.APPROVED) {
            return PaymentWebhookApplyResult.IGNORED_STATE_REGRESSION;
        }
        String requestId = webhookRequestId(provider, payload.getEventId(), "void");
        authorization.voidAuthorization(
            "PROVIDER_WEBHOOK",
            requestId,
            "pv_" + provider + "_" + payload.getEventId()
        );
        return PaymentWebhookApplyResult.APPLIED;
    }

    private PaymentWebhookApplyResult applyRefund(
        String provider,
        Authorization authorization,
        PaymentWebhookPayload payload
    ) {
        requireText(payload.getProviderRefundId(), "providerRefundId");
        if (payload.getAmount() == null || payload.getAmount().isZero()) {
            return PaymentWebhookApplyResult.MANUAL_REVIEW_REQUIRED;
        }
        boolean sameRefund = authorization.getPaymentRefunds().stream()
            .filter(refund -> refund.getStatus() == FinancialOperationStatus.SUCCEEDED)
            .anyMatch(refund -> Objects.equals(
                refund.getProviderRefundId(),
                payload.getProviderRefundId()
            ));
        if (sameRefund) return PaymentWebhookApplyResult.ALREADY_APPLIED;
        if (authorization.getStatus() == AuthorizationStatus.REFUNDED) {
            return PaymentWebhookApplyResult.ALREADY_APPLIED;
        }
        if (authorization.getStatus() != AuthorizationStatus.CAPTURED) {
            return PaymentWebhookApplyResult.IGNORED_STATE_REGRESSION;
        }
        if (payload.getAmount().isGreaterThan(authorization.getRefundableAmount())) {
            return PaymentWebhookApplyResult.MANUAL_REVIEW_REQUIRED;
        }

        PaymentRefund pending = authorization.getPaymentRefunds().stream()
            .filter(refund -> refund.getStatus() == FinancialOperationStatus.PENDING)
            .filter(refund -> refund.getAmount().equals(payload.getAmount()))
            .findFirst()
            .orElse(null);
        String requestId;
        if (pending == null) {
            requestId = webhookRequestId(provider, payload.getEventId(), "refund");
            authorization.requestRefund(
                payload.getAmount(),
                "PROVIDER_WEBHOOK",
                requestId
            );
        } else {
            requestId = pending.getRequestId();
        }
        authorization.completeRefund(requestId, payload.getProviderRefundId());
        return PaymentWebhookApplyResult.APPLIED;
    }

    private void validate(
        String provider,
        PaymentWebhookPayload payload,
        String payloadHash
    ) {
        requireText(provider, "provider");
        if (payload == null) throw new InvalidPaymentWebhookException("Webhook payload is required");
        requireText(payload.getEventId(), "eventId");
        requireText(payload.getProviderAuthorizationId(), "providerAuthorizationId");
        requireText(payloadHash, "payloadHash");
        if (payload.getType() == null) {
            throw new InvalidPaymentWebhookException("Webhook type is required");
        }
    }

    private boolean sameAmount(Money expected, Money actual) {
        return expected != null && expected.equals(actual);
    }

    private String webhookRequestId(String provider, String eventId, String operation) {
        return "webhook-" + provider + "-" + operation + "-" + eventId;
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new InvalidPaymentWebhookException(field + " cannot be null or blank");
        }
        return value;
    }
}
