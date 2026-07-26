package net.ftgo.accounting.reconciliation;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import net.ftgo.accounting.domain.Authorization;
import net.ftgo.accounting.domain.AuthorizationStatus;
import net.ftgo.accounting.domain.FinancialOperationStatus;
import net.ftgo.accounting.domain.PaymentCapture;
import net.ftgo.accounting.domain.PaymentRefund;
import net.ftgo.accounting.payment.PaymentProvider;
import net.ftgo.accounting.payment.PaymentProviderCharge;
import net.ftgo.accounting.payment.PaymentProviderSettlementSnapshot;
import net.ftgo.accounting.payment.PaymentProviderSettlementStatus;
import net.ftgo.accounting.repository.AuthorizationRepository;
import net.ftgo.common.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
public class PaymentReconciliationService {

    private final AuthorizationRepository authorizationRepository;
    private final PaymentReconciliationCaseRepository caseRepository;
    private final PaymentProvider paymentProvider;
    private final Counter safeUpdates;
    private final Counter manualReviewCases;
    private final Counter criticalCases;

    public PaymentReconciliationService(
        AuthorizationRepository authorizationRepository,
        PaymentReconciliationCaseRepository caseRepository,
        PaymentProvider paymentProvider,
        MeterRegistry meterRegistry
    ) {
        this.authorizationRepository = authorizationRepository;
        this.caseRepository = caseRepository;
        this.paymentProvider = paymentProvider;
        this.safeUpdates = Counter.builder(
            "accounting_payment_reconciliation_safe_updates_total")
            .description("Safe local payment state updates from provider reconciliation")
            .register(meterRegistry);
        this.manualReviewCases = Counter.builder(
            "accounting_payment_reconciliation_manual_review_total")
            .description("Payment reconciliation cases requiring manual review")
            .register(meterRegistry);
        this.criticalCases = Counter.builder(
            "accounting_payment_reconciliation_critical_cases_total")
            .description("Critical payment reconciliation cases")
            .register(meterRegistry);
    }

    @Transactional
    public PaymentReconciliationResult reconcileAuthorization(
        String providerAuthorizationId
    ) {
        Authorization authorization = authorizationRepository
            .findByProviderAuthorizationIdForUpdate(providerAuthorizationId)
            .orElse(null);
        if (authorization == null) {
            return PaymentReconciliationResult.UNKNOWN_LOCAL_AUTHORIZATION;
        }

        PaymentProviderSettlementSnapshot snapshot =
            paymentProvider.getSettlement(providerAuthorizationId);
        if (snapshot == null
            || snapshot.status() == PaymentProviderSettlementStatus.NOT_FOUND) {
            upsertCase(
                "provider-auth-not-found:" + providerAuthorizationId,
                authorization,
                providerAuthorizationId,
                null,
                PaymentReconciliationCaseType.PROVIDER_AUTHORIZATION_NOT_FOUND,
                PaymentReconciliationSeverity.HIGH,
                authorization.getAmount(),
                null,
                "Provider authorization was not found during reconciliation"
            );
            return PaymentReconciliationResult.PROVIDER_NOT_FOUND;
        }

        return switch (snapshot.status()) {
            case AUTHORIZED -> reconcileAuthorized(authorization, snapshot);
            case CAPTURED -> reconcileCaptured(authorization, snapshot);
            case VOIDED -> reconcileVoided(authorization, snapshot);
            case REFUNDED -> reconcileRefunded(authorization, snapshot);
            case NOT_FOUND -> PaymentReconciliationResult.PROVIDER_NOT_FOUND;
        };
    }

    @Transactional
    public PaymentReconciliationResult reconcileUnknownCharge(
        PaymentProviderCharge charge
    ) {
        if (charge == null
            || charge.providerChargeId() == null
            || charge.providerChargeId().isBlank()) {
            throw new IllegalArgumentException("Provider charge ID is required");
        }
        upsertCase(
            "unknown-provider-charge:" + charge.providerChargeId(),
            null,
            charge.providerAuthorizationId(),
            charge.providerChargeId(),
            PaymentReconciliationCaseType.UNKNOWN_PROVIDER_CHARGE,
            PaymentReconciliationSeverity.CRITICAL,
            null,
            charge.amount(),
            "Provider reported a charge without a matching local financial operation"
        );
        criticalCases.increment();
        // Deliberately no automatic refund: ownership and legitimacy are ambiguous.
        return PaymentReconciliationResult.CRITICAL_CASE;
    }

    private PaymentReconciliationResult reconcileAuthorized(
        Authorization authorization,
        PaymentProviderSettlementSnapshot snapshot
    ) {
        if (!sameAmount(authorization.getAmount(), snapshot.authorizedAmount())) {
            return amountMismatch(authorization, snapshot.authorizedAmount(), "authorized");
        }
        if (authorization.getStatus() == AuthorizationStatus.AUTHORIZED
            || authorization.getStatus() == AuthorizationStatus.APPROVED) {
            return PaymentReconciliationResult.NO_CHANGE;
        }
        return stateConflict(authorization, snapshot, "Provider remains authorized");
    }

    private PaymentReconciliationResult reconcileCaptured(
        Authorization authorization,
        PaymentProviderSettlementSnapshot snapshot
    ) {
        if (!sameAmount(authorization.getAmount(), snapshot.capturedAmount())) {
            return amountMismatch(authorization, snapshot.capturedAmount(), "captured");
        }
        if (snapshot.providerCaptureId() == null
            || snapshot.providerCaptureId().isBlank()) {
            return stateConflict(authorization, snapshot, "Provider capture reference is missing");
        }
        if (authorization.getStatus() == AuthorizationStatus.CAPTURED) {
            boolean sameCapture = authorization.getPaymentCaptures().stream()
                .filter(capture -> capture.getStatus() == FinancialOperationStatus.SUCCEEDED)
                .anyMatch(capture -> Objects.equals(
                    capture.getProviderCaptureId(),
                    snapshot.providerCaptureId()
                ));
            return sameCapture
                ? PaymentReconciliationResult.NO_CHANGE
                : stateConflict(authorization, snapshot, "Provider capture reference differs");
        }
        if (authorization.getStatus() != AuthorizationStatus.AUTHORIZED
            && authorization.getStatus() != AuthorizationStatus.APPROVED) {
            return stateConflict(authorization, snapshot, "Provider captured after local terminal state");
        }

        PaymentCapture pending = authorization.getPaymentCaptures().stream()
            .filter(capture -> capture.getStatus() == FinancialOperationStatus.PENDING)
            .findFirst()
            .orElse(null);
        if (pending == null && !authorization.getPaymentCaptures().isEmpty()) {
            return stateConflict(authorization, snapshot, "Local capture is terminal but provider captured");
        }
        String requestId;
        if (pending == null) {
            requestId = "reconcile-capture-" + authorization.getProviderAuthorizationId();
            authorization.requestCapture(requestId);
        } else {
            requestId = pending.getRequestId();
        }
        authorization.completeCapture(requestId, snapshot.providerCaptureId());
        authorizationRepository.saveAndFlush(authorization);
        safeUpdates.increment();
        return PaymentReconciliationResult.SAFE_UPDATE;
    }

    private PaymentReconciliationResult reconcileVoided(
        Authorization authorization,
        PaymentProviderSettlementSnapshot snapshot
    ) {
        if (!sameAmount(authorization.getAmount(), snapshot.authorizedAmount())) {
            return amountMismatch(authorization, snapshot.authorizedAmount(), "voided");
        }
        if (authorization.getStatus() == AuthorizationStatus.VOIDED) {
            return PaymentReconciliationResult.NO_CHANGE;
        }
        if (authorization.getStatus() != AuthorizationStatus.AUTHORIZED
            && authorization.getStatus() != AuthorizationStatus.APPROVED) {
            return stateConflict(authorization, snapshot, "Provider void conflicts with local state");
        }
        String providerVoidId = snapshot.providerVoidId() == null
            || snapshot.providerVoidId().isBlank()
            ? "pv_reconcile_" + authorization.getProviderAuthorizationId()
            : snapshot.providerVoidId();
        authorization.voidAuthorization(
            "PROVIDER_RECONCILIATION",
            "reconcile-void-" + authorization.getProviderAuthorizationId(),
            providerVoidId
        );
        authorizationRepository.saveAndFlush(authorization);
        safeUpdates.increment();
        return PaymentReconciliationResult.SAFE_UPDATE;
    }

    private PaymentReconciliationResult reconcileRefunded(
        Authorization authorization,
        PaymentProviderSettlementSnapshot snapshot
    ) {
        Money providerRefunded = snapshot.refundedAmount();
        if (providerRefunded == null || providerRefunded.isZero()) {
            return stateConflict(authorization, snapshot, "Provider refund amount is missing");
        }
        if (providerRefunded.isGreaterThan(authorization.getAmount())) {
            return amountMismatch(authorization, providerRefunded, "refunded");
        }
        if (authorization.getStatus() == AuthorizationStatus.REFUNDED
            || authorization.getRefundedAmount().equals(providerRefunded)) {
            return PaymentReconciliationResult.NO_CHANGE;
        }
        if (authorization.getStatus() != AuthorizationStatus.CAPTURED) {
            return stateConflict(authorization, snapshot, "Provider refund conflicts with local state");
        }
        if (snapshot.providerRefundId() == null || snapshot.providerRefundId().isBlank()) {
            return stateConflict(authorization, snapshot, "Provider refund reference is missing");
        }

        PaymentRefund pending = authorization.getPaymentRefunds().stream()
            .filter(refund -> refund.getStatus() == FinancialOperationStatus.PENDING)
            .filter(refund -> refund.getAmount().equals(providerRefunded))
            .findFirst()
            .orElse(null);
        String requestId;
        if (pending == null) {
            requestId = "reconcile-refund-" + snapshot.providerRefundId();
            authorization.requestRefund(
                providerRefunded,
                "PROVIDER_RECONCILIATION",
                requestId
            );
        } else {
            requestId = pending.getRequestId();
        }
        authorization.completeRefund(requestId, snapshot.providerRefundId());
        authorizationRepository.saveAndFlush(authorization);
        safeUpdates.increment();
        return PaymentReconciliationResult.SAFE_UPDATE;
    }

    private PaymentReconciliationResult amountMismatch(
        Authorization authorization,
        Money providerAmount,
        String providerState
    ) {
        upsertCase(
            "amount-mismatch:" + authorization.getProviderAuthorizationId()
                + ":" + providerState + ":" + String.valueOf(providerAmount),
            authorization,
            authorization.getProviderAuthorizationId(),
            null,
            PaymentReconciliationCaseType.AMOUNT_MISMATCH,
            PaymentReconciliationSeverity.HIGH,
            authorization.getAmount(),
            providerAmount,
            "Provider " + providerState + " amount differs from the local authorization"
        );
        manualReviewCases.increment();
        return PaymentReconciliationResult.MANUAL_REVIEW;
    }

    private PaymentReconciliationResult stateConflict(
        Authorization authorization,
        PaymentProviderSettlementSnapshot snapshot,
        String summary
    ) {
        upsertCase(
            "state-conflict:" + authorization.getProviderAuthorizationId()
                + ":" + snapshot.status(),
            authorization,
            authorization.getProviderAuthorizationId(),
            firstProviderReference(snapshot),
            PaymentReconciliationCaseType.PROVIDER_STATE_CONFLICT,
            PaymentReconciliationSeverity.HIGH,
            authorization.getAmount(),
            snapshot.capturedAmount(),
            summary
        );
        manualReviewCases.increment();
        return PaymentReconciliationResult.MANUAL_REVIEW;
    }

    private PaymentReconciliationCase upsertCase(
        String caseKey,
        Authorization authorization,
        String providerAuthorizationId,
        String providerReference,
        PaymentReconciliationCaseType type,
        PaymentReconciliationSeverity severity,
        Money expectedAmount,
        Money providerAmount,
        String summary
    ) {
        PaymentReconciliationCase value = caseRepository.findByCaseKey(caseKey)
            .map(existing -> {
                existing.seenAgain();
                return existing;
            })
            .orElseGet(() -> new PaymentReconciliationCase(
                caseKey,
                authorization == null ? null : authorization.getId(),
                providerAuthorizationId,
                providerReference,
                type,
                severity,
                expectedAmount,
                providerAmount,
                summary
            ));
        return caseRepository.saveAndFlush(value);
    }

    private String firstProviderReference(PaymentProviderSettlementSnapshot snapshot) {
        if (snapshot.providerCaptureId() != null) return snapshot.providerCaptureId();
        if (snapshot.providerRefundId() != null) return snapshot.providerRefundId();
        return snapshot.providerVoidId();
    }

    private boolean sameAmount(Money expected, Money actual) {
        return expected != null && expected.equals(actual);
    }
}
