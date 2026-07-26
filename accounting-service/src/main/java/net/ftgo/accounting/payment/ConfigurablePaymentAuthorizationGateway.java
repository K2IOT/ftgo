package net.ftgo.accounting.payment;

import net.ftgo.common.Money;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Deterministic sandbox provider used until a real provider integration is
 * configured. References are derived from idempotency keys and an in-memory
 * ledger exposes provider settlement state for reconciliation and E2E checks.
 */
@Component
public class ConfigurablePaymentAuthorizationGateway implements PaymentProvider {

    public static final String PROVIDER_DECLINED = "PAYMENT_PROVIDER_DECLINED";
    public static final String TOKEN_REQUIRED = "PAYMENT_TOKEN_REQUIRED";
    public static final String CAPTURE_DECLINED = "PAYMENT_CAPTURE_DECLINED";

    private final Set<String> declinedTokens;
    private final Map<String, PaymentAuthorizationDecision> authorizationsByRequest = new HashMap<>();
    private final Map<String, Settlement> settlementsByAuthorization = new HashMap<>();
    private final Map<String, String> authorizationByCapture = new HashMap<>();
    private final Map<String, PaymentProviderResult> capturesByRequest = new HashMap<>();
    private final Map<String, PaymentProviderResult> voidsByRequest = new HashMap<>();
    private final Map<String, PaymentProviderResult> refundsByRequest = new HashMap<>();
    private final Map<String, Long> operationCounts = new HashMap<>();
    private final Set<String> transientCaptureFailures = new HashSet<>();

    public ConfigurablePaymentAuthorizationGateway(
        @Value("${ftgo.accounting.declined-payment-tokens:}") String declinedPaymentTokens
    ) {
        this.declinedTokens = Arrays.stream(declinedPaymentTokens.split(","))
            .map(String::trim)
            .filter(token -> !token.isEmpty())
            .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public PaymentAuthorizationDecision authorize(String paymentToken, Money amount) {
        return authorize(
            paymentToken,
            amount,
            "legacy-" + stableReference("authorization", String.valueOf(paymentToken) + ":" + amount)
        );
    }

    @Override
    public synchronized PaymentAuthorizationDecision authorize(
        String paymentToken,
        Money amount,
        String requestId
    ) {
        requireMoney(amount);
        requireText(requestId, "requestId");
        PaymentAuthorizationDecision replay = authorizationsByRequest.get(requestId);
        if (replay != null) {
            return replay;
        }

        PaymentAuthorizationDecision decision;
        if (paymentToken == null) {
            decision = PaymentAuthorizationDecision.allow(stableReference("pa", requestId));
        } else if (paymentToken.isBlank()) {
            decision = PaymentAuthorizationDecision.denied(TOKEN_REQUIRED);
        } else if (declinedTokens.contains(paymentToken)) {
            decision = PaymentAuthorizationDecision.denied(PROVIDER_DECLINED);
        } else {
            decision = PaymentAuthorizationDecision.allow(stableReference("pa", requestId));
        }

        authorizationsByRequest.put(requestId, decision);
        increment("authorize");
        if (decision.approved()) {
            settlementsByAuthorization.putIfAbsent(
                decision.providerAuthorizationId(),
                Settlement.authorized(decision.providerAuthorizationId(), amount)
            );
        }
        return decision;
    }

    @Override
    public synchronized PaymentProviderResult capture(
        String providerAuthorizationId,
        Money amount,
        String requestId
    ) {
        requireText(providerAuthorizationId, "providerAuthorizationId");
        requireMoney(amount);
        requireText(requestId, "requestId");
        PaymentProviderResult replay = capturesByRequest.get(requestId);
        if (replay != null) {
            return replay;
        }
        if (providerAuthorizationId.startsWith("pa_capture_error_always_")) {
            throw new RetryablePaymentProviderException("Sandbox capture provider unavailable");
        }
        if (providerAuthorizationId.startsWith("pa_capture_error_")
            && transientCaptureFailures.add(requestId)) {
            throw new RetryablePaymentProviderException("Sandbox capture provider unavailable");
        }

        PaymentProviderResult result = providerAuthorizationId.startsWith("pa_capture_decline_")
            ? PaymentProviderResult.declined(CAPTURE_DECLINED)
            : PaymentProviderResult.succeeded(stableReference("pc", requestId));
        capturesByRequest.put(requestId, result);
        increment("capture");

        if (result.successful()) {
            Settlement settlement = settlementsByAuthorization.computeIfAbsent(
                providerAuthorizationId,
                ignored -> Settlement.authorized(providerAuthorizationId, amount)
            );
            settlement.capture(result.providerReference(), amount);
            authorizationByCapture.put(result.providerReference(), providerAuthorizationId);
        }
        return result;
    }

    @Override
    public synchronized PaymentProviderResult voidAuthorization(
        String providerAuthorizationId,
        String requestId
    ) {
        requireText(providerAuthorizationId, "providerAuthorizationId");
        requireText(requestId, "requestId");
        PaymentProviderResult replay = voidsByRequest.get(requestId);
        if (replay != null) {
            return replay;
        }
        if (providerAuthorizationId.startsWith("pa_void_error_")) {
            throw new RetryablePaymentProviderException("Sandbox void provider unavailable");
        }

        PaymentProviderResult result = PaymentProviderResult.succeeded(stableReference("pv", requestId));
        voidsByRequest.put(requestId, result);
        increment("void");
        Settlement settlement = settlementsByAuthorization.computeIfAbsent(
            providerAuthorizationId,
            ignored -> Settlement.authorized(providerAuthorizationId, Money.ZERO)
        );
        settlement.voidAuthorization(result.providerReference());
        return result;
    }

    @Override
    public synchronized PaymentProviderResult refund(
        String providerCaptureId,
        Money amount,
        String requestId
    ) {
        requireText(providerCaptureId, "providerCaptureId");
        requireMoney(amount);
        requireText(requestId, "requestId");
        PaymentProviderResult replay = refundsByRequest.get(requestId);
        if (replay != null) {
            return replay;
        }
        if (requestId.startsWith("refund-error-")) {
            throw new RetryablePaymentProviderException("Sandbox refund provider unavailable");
        }

        PaymentProviderResult result = PaymentProviderResult.succeeded(stableReference("pr", requestId));
        refundsByRequest.put(requestId, result);
        increment("refund");
        String providerAuthorizationId = authorizationByCapture.get(providerCaptureId);
        if (providerAuthorizationId != null) {
            settlementsByAuthorization.get(providerAuthorizationId)
                .refund(result.providerReference(), amount);
        }
        return result;
    }

    @Override
    public synchronized PaymentProviderSettlementSnapshot getSettlement(String providerAuthorizationId) {
        Settlement settlement = settlementsByAuthorization.get(providerAuthorizationId);
        return settlement == null
            ? PaymentProviderSettlementSnapshot.notFound(providerAuthorizationId)
            : settlement.snapshot();
    }

    @Override
    public synchronized List<PaymentProviderCharge> listRecentCharges(Instant since) {
        List<PaymentProviderCharge> charges = new ArrayList<>();
        for (Settlement settlement : settlementsByAuthorization.values()) {
            if (settlement.providerCaptureId != null && !settlement.updatedAt.isBefore(since)) {
                charges.add(new PaymentProviderCharge(
                    settlement.providerAuthorizationId,
                    settlement.providerCaptureId,
                    settlement.capturedAmount,
                    settlement.updatedAt
                ));
            }
        }
        return List.copyOf(charges);
    }

    public synchronized long getOperationCount(String operation) {
        return operationCounts.getOrDefault(operation, 0L);
    }

    public synchronized Map<String, Long> getOperationCounts() {
        return Map.copyOf(operationCounts);
    }

    public synchronized void resetSandboxLedger() {
        authorizationsByRequest.clear();
        settlementsByAuthorization.clear();
        authorizationByCapture.clear();
        capturesByRequest.clear();
        voidsByRequest.clear();
        refundsByRequest.clear();
        operationCounts.clear();
        transientCaptureFailures.clear();
    }

    private void increment(String operation) {
        operationCounts.merge(operation, 1L, Long::sum);
    }

    private static String stableReference(String prefix, String idempotencyKey) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(idempotencyKey.getBytes(StandardCharsets.UTF_8));
            return prefix + "_" + HexFormat.of().formatHex(digest, 0, 12);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be null or blank");
        }
    }

    private static void requireMoney(Money amount) {
        if (amount == null || amount.isZero()) {
            throw new IllegalArgumentException("Payment amount must be positive");
        }
    }

    private static final class Settlement {
        private final String providerAuthorizationId;
        private final Money authorizedAmount;
        private PaymentProviderSettlementStatus status;
        private String providerCaptureId;
        private String providerVoidId;
        private String providerRefundId;
        private Money capturedAmount = Money.ZERO;
        private Money refundedAmount = Money.ZERO;
        private Instant updatedAt = Instant.now();

        private Settlement(String providerAuthorizationId, Money authorizedAmount) {
            this.providerAuthorizationId = providerAuthorizationId;
            this.authorizedAmount = authorizedAmount;
            this.status = PaymentProviderSettlementStatus.AUTHORIZED;
        }

        static Settlement authorized(String providerAuthorizationId, Money amount) {
            return new Settlement(providerAuthorizationId, amount);
        }

        void capture(String captureId, Money amount) {
            if (status == PaymentProviderSettlementStatus.VOIDED) {
                throw new IllegalStateException("Voided authorization cannot be captured");
            }
            providerCaptureId = captureId;
            capturedAmount = amount;
            status = PaymentProviderSettlementStatus.CAPTURED;
            updatedAt = Instant.now();
        }

        void voidAuthorization(String voidId) {
            if (status == PaymentProviderSettlementStatus.CAPTURED
                || status == PaymentProviderSettlementStatus.REFUNDED) {
                throw new IllegalStateException("Captured authorization cannot be voided");
            }
            providerVoidId = voidId;
            status = PaymentProviderSettlementStatus.VOIDED;
            updatedAt = Instant.now();
        }

        void refund(String refundId, Money amount) {
            if (status != PaymentProviderSettlementStatus.CAPTURED
                && status != PaymentProviderSettlementStatus.REFUNDED) {
                throw new IllegalStateException("Only captured payment can be refunded");
            }
            Money nextRefunded = refundedAmount.add(amount);
            if (nextRefunded.isGreaterThan(capturedAmount)) {
                throw new IllegalStateException("Provider refund exceeds captured amount");
            }
            providerRefundId = refundId;
            refundedAmount = nextRefunded;
            status = refundedAmount.equals(capturedAmount)
                ? PaymentProviderSettlementStatus.REFUNDED
                : PaymentProviderSettlementStatus.CAPTURED;
            updatedAt = Instant.now();
        }

        PaymentProviderSettlementSnapshot snapshot() {
            return new PaymentProviderSettlementSnapshot(
                providerAuthorizationId,
                status,
                providerCaptureId,
                providerVoidId,
                providerRefundId,
                authorizedAmount,
                capturedAmount,
                refundedAmount
            );
        }
    }
}
