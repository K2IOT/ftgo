package net.ftgo.accounting.reconciliation;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.ftgo.accounting.domain.Authorization;
import net.ftgo.accounting.domain.AuthorizationStatus;
import net.ftgo.accounting.payment.PaymentProvider;
import net.ftgo.accounting.payment.PaymentProviderCharge;
import net.ftgo.accounting.payment.PaymentProviderSettlementSnapshot;
import net.ftgo.accounting.repository.AuthorizationRepository;
import net.ftgo.common.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentReconciliationServiceTest {

    @Mock
    private AuthorizationRepository authorizationRepository;

    @Mock
    private PaymentReconciliationCaseRepository caseRepository;

    @Mock
    private PaymentProvider paymentProvider;

    private SimpleMeterRegistry meterRegistry;
    private PaymentReconciliationService service;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        service = new PaymentReconciliationService(
            authorizationRepository,
            caseRepository,
            paymentProvider,
            meterRegistry
        );
    }

    @Test
    void localPendingAndProviderCapturedUpdatesSafelyOnce() {
        Authorization authorization = authorized();
        authorization.requestCapture("capture-order-101");
        when(authorizationRepository.findByProviderAuthorizationIdForUpdate("pa_order_101"))
            .thenReturn(Optional.of(authorization));
        when(paymentProvider.getSettlement("pa_order_101"))
            .thenReturn(PaymentProviderSettlementSnapshot.captured(
                "pa_order_101",
                "pc_order_101",
                new Money("25.00")
            ));

        PaymentReconciliationResult first =
            service.reconcileAuthorization("pa_order_101");
        PaymentReconciliationResult repeated =
            service.reconcileAuthorization("pa_order_101");

        assertThat(first).isEqualTo(PaymentReconciliationResult.SAFE_UPDATE);
        assertThat(repeated).isEqualTo(PaymentReconciliationResult.NO_CHANGE);
        assertThat(authorization.getStatus()).isEqualTo(AuthorizationStatus.CAPTURED);
        assertThat(authorization.getSuccessfulCapture().getProviderCaptureId())
            .isEqualTo("pc_order_101");
        verify(authorizationRepository, times(1)).saveAndFlush(authorization);
        assertThat(meterRegistry
            .get("accounting_payment_reconciliation_safe_updates_total")
            .counter().count()).isEqualTo(1.0);
    }

    @Test
    void localAuthorizedAndProviderVoidedUpdatesSafely() {
        Authorization authorization = authorized();
        when(authorizationRepository.findByProviderAuthorizationIdForUpdate("pa_order_101"))
            .thenReturn(Optional.of(authorization));
        when(paymentProvider.getSettlement("pa_order_101"))
            .thenReturn(PaymentProviderSettlementSnapshot.voided(
                "pa_order_101",
                "pv_order_101",
                new Money("25.00")
            ));

        PaymentReconciliationResult result =
            service.reconcileAuthorization("pa_order_101");

        assertThat(result).isEqualTo(PaymentReconciliationResult.SAFE_UPDATE);
        assertThat(authorization.getStatus()).isEqualTo(AuthorizationStatus.VOIDED);
        assertThat(authorization.getProviderVoidId()).isEqualTo("pv_order_101");
        verify(authorizationRepository).saveAndFlush(authorization);
    }

    @Test
    void amountMismatchCreatesOneRepeatableManualReviewCase() {
        Authorization authorization = authorized();
        when(authorizationRepository.findByProviderAuthorizationIdForUpdate("pa_order_101"))
            .thenReturn(Optional.of(authorization));
        when(paymentProvider.getSettlement("pa_order_101"))
            .thenReturn(PaymentProviderSettlementSnapshot.captured(
                "pa_order_101",
                "pc_order_101",
                new Money("30.00")
            ));
        AtomicReference<PaymentReconciliationCase> stored = new AtomicReference<>();
        when(caseRepository.findByCaseKey(anyString()))
            .thenAnswer(invocation -> Optional.ofNullable(stored.get()));
        when(caseRepository.saveAndFlush(any(PaymentReconciliationCase.class)))
            .thenAnswer(invocation -> {
                PaymentReconciliationCase value = invocation.getArgument(0);
                stored.compareAndSet(null, value);
                return value;
            });

        PaymentReconciliationResult first =
            service.reconcileAuthorization("pa_order_101");
        PaymentReconciliationResult repeated =
            service.reconcileAuthorization("pa_order_101");

        assertThat(first).isEqualTo(PaymentReconciliationResult.MANUAL_REVIEW);
        assertThat(repeated).isEqualTo(PaymentReconciliationResult.MANUAL_REVIEW);
        assertThat(stored.get().getCaseType())
            .isEqualTo(PaymentReconciliationCaseType.AMOUNT_MISMATCH);
        assertThat(stored.get().getOccurrences()).isEqualTo(2);
        verify(authorizationRepository, never()).saveAndFlush(authorization);
    }

    @Test
    void unknownExtraChargeCreatesCriticalCaseAndNeverAutoRefunds() {
        AtomicReference<PaymentReconciliationCase> stored = new AtomicReference<>();
        when(caseRepository.findByCaseKey(anyString()))
            .thenAnswer(invocation -> Optional.ofNullable(stored.get()));
        when(caseRepository.saveAndFlush(any(PaymentReconciliationCase.class)))
            .thenAnswer(invocation -> {
                PaymentReconciliationCase value = invocation.getArgument(0);
                stored.compareAndSet(null, value);
                return value;
            });
        PaymentProviderCharge charge = new PaymentProviderCharge(
            "charge_unknown_101",
            "pa_unknown_101",
            new Money("25.00"),
            Instant.parse("2026-07-25T16:00:00Z")
        );

        PaymentReconciliationResult result = service.reconcileUnknownCharge(charge);

        assertThat(result).isEqualTo(PaymentReconciliationResult.CRITICAL_CASE);
        assertThat(stored.get().getCaseType())
            .isEqualTo(PaymentReconciliationCaseType.UNKNOWN_PROVIDER_CHARGE);
        assertThat(stored.get().getSeverity())
            .isEqualTo(PaymentReconciliationSeverity.CRITICAL);
        verify(paymentProvider, never()).refund(anyString(), any(Money.class), anyString());
    }

    private Authorization authorized() {
        return new Authorization(
            101L,
            "authorize-order-101",
            new Money("25.00"),
            "pa_order_101",
            AuthorizationStatus.AUTHORIZED
        );
    }
}
