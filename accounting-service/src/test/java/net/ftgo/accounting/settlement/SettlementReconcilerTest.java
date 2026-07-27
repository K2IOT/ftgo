package net.ftgo.accounting.settlement;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.ftgo.accounting.domain.Authorization;
import net.ftgo.accounting.domain.AuthorizationStatus;
import net.ftgo.accounting.repository.AuthorizationRepository;
import net.ftgo.common.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettlementReconcilerTest {

    @Mock
    private AuthorizationRepository authorizationRepository;

    @Mock
    private SettlementGateway settlementGateway;

    @Mock
    private SettlementDiscrepancyRepository discrepancyRepository;

    @Mock
    private PaymentLedgerEntryRepository ledgerRepository;

    private SettlementReconciler reconciler;

    @BeforeEach
    void setUp() {
        reconciler = new SettlementReconciler(
            authorizationRepository,
            settlementGateway,
            discrepancyRepository,
            ledgerRepository,
            new SimpleMeterRegistry(),
            Clock.fixed(Instant.parse("2026-07-27T02:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void providerMissingCreatesOnePersistentDiscrepancyAcrossRepeatedScans() {
        Authorization authorization = captured(701L, 101L, "42.50");
        when(authorizationRepository.findAll()).thenReturn(List.of(authorization));
        when(settlementGateway.find(701L)).thenReturn(Optional.empty());
        when(ledgerRepository.findByAuthorizationIdOrderByOccurredAtAsc(701L))
            .thenReturn(ledgerForCaptured(701L, 101L, "42.50"));
        when(discrepancyRepository.findByFingerprint("701:PROVIDER_MISSING"))
            .thenReturn(Optional.empty())
            .thenReturn(Optional.of(new SettlementDiscrepancy(
                701L,
                101L,
                SettlementDiscrepancyType.PROVIDER_MISSING,
                "701:PROVIDER_MISSING",
                "CAPTURED",
                "MISSING",
                "Provider settlement is missing",
                Instant.parse("2026-07-27T02:00:00Z")
            )));
        when(discrepancyRepository.findByStatusIn(any())).thenReturn(List.of());
        when(discrepancyRepository.saveAndFlush(any(SettlementDiscrepancy.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        reconciler.scan();
        reconciler.scan();

        verify(discrepancyRepository, times(1)).saveAndFlush(any(SettlementDiscrepancy.class));
    }

    @Test
    void matchingProviderAndLedgerResolvePreviouslyOpenDiscrepancy() {
        Authorization authorization = captured(702L, 102L, "25.00");
        SettlementDiscrepancy open = new SettlementDiscrepancy(
            702L,
            102L,
            SettlementDiscrepancyType.STATUS_MISMATCH,
            "702:STATUS_MISMATCH",
            "CAPTURED",
            "AUTHORIZED",
            "Provider status differs from local status",
            Instant.parse("2026-07-27T01:00:00Z")
        );
        when(authorizationRepository.findAll()).thenReturn(List.of(authorization));
        when(settlementGateway.find(702L)).thenReturn(Optional.of(new ProviderSettlementSnapshot(
            702L,
            102L,
            new Money("25.00"),
            new Money("25.00"),
            Money.ZERO,
            ProviderSettlementStatus.CAPTURED,
            "provider-capture-702"
        )));
        when(ledgerRepository.findByAuthorizationIdOrderByOccurredAtAsc(702L))
            .thenReturn(ledgerForCaptured(702L, 102L, "25.00"));
        when(discrepancyRepository.findByStatusIn(any())).thenReturn(List.of(open));
        when(discrepancyRepository.saveAndFlush(open)).thenReturn(open);

        SettlementReconciliationReport report = reconciler.scan();

        assertThat(report.detected()).isZero();
        assertThat(report.resolved()).isEqualTo(1);
        assertThat(open.getStatus()).isEqualTo(SettlementDiscrepancyStatus.RESOLVED);
        verify(discrepancyRepository).saveAndFlush(open);
    }

    @Test
    void statusAndRefundMismatchAreClassifiedSeparately() {
        Authorization authorization = captured(703L, 103L, "100.00");
        authorization.refund(new Money("30.00"), "partial", "refund-703-1");
        when(authorizationRepository.findAll()).thenReturn(List.of(authorization));
        when(settlementGateway.find(703L)).thenReturn(Optional.of(new ProviderSettlementSnapshot(
            703L,
            103L,
            new Money("100.00"),
            new Money("100.00"),
            Money.ZERO,
            ProviderSettlementStatus.CAPTURED,
            "provider-capture-703"
        )));
        when(ledgerRepository.findByAuthorizationIdOrderByOccurredAtAsc(703L))
            .thenReturn(List.of(
                ledger(703L, 103L, PaymentLedgerEntry.OperationType.AUTHORIZE, "authorize-703", "100.00"),
                ledger(703L, 103L, PaymentLedgerEntry.OperationType.CAPTURE, "capture-703", "100.00"),
                ledger(703L, 103L, PaymentLedgerEntry.OperationType.REFUND, "refund-703-1", "30.00")
            ));
        when(discrepancyRepository.findByFingerprint(any())).thenReturn(Optional.empty());
        when(discrepancyRepository.findByStatusIn(any())).thenReturn(List.of());
        when(discrepancyRepository.saveAndFlush(any(SettlementDiscrepancy.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        SettlementReconciliationReport report = reconciler.scan();

        assertThat(report.detected()).isEqualTo(2);
        verify(discrepancyRepository).findByFingerprint("703:STATUS_MISMATCH");
        verify(discrepancyRepository).findByFingerprint("703:REFUND_AMOUNT_MISMATCH");
    }

    @Test
    void syncProviderRepairUsesIdempotencyKeyOnceAndResolvesDiscrepancy() {
        Authorization authorization = captured(704L, 104L, "60.00");
        SettlementDiscrepancy discrepancy = new SettlementDiscrepancy(
            704L,
            104L,
            SettlementDiscrepancyType.PROVIDER_MISSING,
            "704:PROVIDER_MISSING",
            "CAPTURED",
            "MISSING",
            "Provider settlement is missing",
            Instant.parse("2026-07-27T01:00:00Z")
        );
        ReflectionTestUtils.setField(discrepancy, "id", 9001L);
        when(discrepancyRepository.findById(9001L)).thenReturn(Optional.of(discrepancy));
        when(authorizationRepository.findById(704L)).thenReturn(Optional.of(authorization));
        when(settlementGateway.synchronize(
            any(SettlementTarget.class),
            eq("repair-704-1")
        )).thenReturn(SettlementDecision.approved("provider-repair-704"));
        when(discrepancyRepository.saveAndFlush(discrepancy)).thenReturn(discrepancy);

        SettlementDiscrepancy first = reconciler.repair(
            9001L,
            SettlementRepairAction.SYNC_PROVIDER_FROM_LOCAL,
            "repair-704-1",
            "restore missing provider state"
        );
        SettlementDiscrepancy replay = reconciler.repair(
            9001L,
            SettlementRepairAction.SYNC_PROVIDER_FROM_LOCAL,
            "repair-704-1",
            "restore missing provider state"
        );

        assertThat(first).isSameAs(replay);
        assertThat(discrepancy.getStatus()).isEqualTo(SettlementDiscrepancyStatus.RESOLVED);
        assertThat(discrepancy.getRepairRequestId()).isEqualTo("repair-704-1");
        verify(settlementGateway, times(1)).synchronize(
            any(SettlementTarget.class),
            eq("repair-704-1")
        );
    }

    @Test
    void acknowledgeRepairDoesNotMutateProvider() {
        SettlementDiscrepancy discrepancy = new SettlementDiscrepancy(
            705L,
            105L,
            SettlementDiscrepancyType.LEDGER_MISMATCH,
            "705:LEDGER_MISMATCH",
            "CAPTURED",
            "CAPTURED",
            "Ledger capture total differs from local state",
            Instant.parse("2026-07-27T01:00:00Z")
        );
        ReflectionTestUtils.setField(discrepancy, "id", 9002L);
        when(discrepancyRepository.findById(9002L)).thenReturn(Optional.of(discrepancy));
        when(discrepancyRepository.saveAndFlush(discrepancy)).thenReturn(discrepancy);

        reconciler.repair(
            9002L,
            SettlementRepairAction.ACKNOWLEDGE,
            "ack-705-1",
            "accepted for manual investigation"
        );

        assertThat(discrepancy.getStatus()).isEqualTo(SettlementDiscrepancyStatus.ACKNOWLEDGED);
        verify(settlementGateway, never()).synchronize(any(), any());
    }

    private Authorization captured(Long authorizationId, Long orderId, String amount) {
        Authorization authorization = new Authorization(
            501L,
            orderId,
            "authorize-" + authorizationId,
            new Money(amount),
            AuthorizationStatus.AUTHORIZED
        );
        ReflectionTestUtils.setField(authorization, "id", authorizationId);
        authorization.capture("capture-" + authorizationId);
        return authorization;
    }

    private List<PaymentLedgerEntry> ledgerForCaptured(
        Long authorizationId,
        Long orderId,
        String amount
    ) {
        return List.of(
            ledger(
                authorizationId,
                orderId,
                PaymentLedgerEntry.OperationType.AUTHORIZE,
                "authorize-" + authorizationId,
                amount
            ),
            ledger(
                authorizationId,
                orderId,
                PaymentLedgerEntry.OperationType.CAPTURE,
                "capture-" + authorizationId,
                amount
            )
        );
    }

    private PaymentLedgerEntry ledger(
        Long authorizationId,
        Long orderId,
        PaymentLedgerEntry.OperationType operation,
        String requestId,
        String amount
    ) {
        return new PaymentLedgerEntry(
            501L,
            orderId,
            authorizationId,
            operation,
            requestId,
            new Money(amount).getAmount(),
            "provider-" + requestId
        );
    }
}