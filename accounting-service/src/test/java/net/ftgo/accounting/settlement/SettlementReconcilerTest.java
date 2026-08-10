package net.ftgo.accounting.settlement;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.ftgo.accounting.domain.Authorization;
import net.ftgo.accounting.domain.AuthorizationStatus;
import net.ftgo.accounting.repository.AuthorizationRepository;
import net.ftgo.common.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
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

    private static final Instant NOW = Instant.parse("2026-07-27T02:00:00Z");

    @Mock
    private AuthorizationRepository authorizationRepository;

    @Mock
    private SettlementGateway settlementGateway;

    @Mock
    private SettlementDiscrepancyRepository discrepancyRepository;

    @Mock
    private PaymentLedgerEntryRepository ledgerRepository;

    @Mock
    private SettlementReconciliationWorkRepository workRepository;

    private SettlementReconciler reconciler;

    @BeforeEach
    void setUp() {
        reconciler = new SettlementReconciler(
            authorizationRepository,
            settlementGateway,
            discrepancyRepository,
            ledgerRepository,
            workRepository,
            new SimpleMeterRegistry(),
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void providerMissingCreatesOnePersistentDiscrepancyAcrossRepeatedScans() {
        Authorization authorization = captured(701L, 101L, "42.50");
        SettlementDiscrepancy existing = new SettlementDiscrepancy(
            701L,
            101L,
            SettlementDiscrepancyType.PROVIDER_MISSING,
            "701:PROVIDER_MISSING",
            "CAPTURED",
            "MISSING",
            "Provider settlement is missing",
            NOW
        );
        queueTwice(authorization, "42.50", "0.00");
        when(settlementGateway.find(701L)).thenReturn(Optional.empty());
        when(discrepancyRepository.findByAuthorizationIdIn(any()))
            .thenReturn(List.of())
            .thenReturn(List.of(existing));
        when(discrepancyRepository.saveAndFlush(any(SettlementDiscrepancy.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        reconciler.scan();
        reconciler.scan();

        verify(discrepancyRepository, times(1)).saveAndFlush(any(SettlementDiscrepancy.class));
        verify(authorizationRepository, never()).findAll();
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
        queue(authorization, "25.00", "0.00");
        when(settlementGateway.find(702L)).thenReturn(Optional.of(new ProviderSettlementSnapshot(
            702L,
            102L,
            new Money("25.00"),
            new Money("25.00"),
            Money.ZERO,
            ProviderSettlementStatus.CAPTURED,
            "provider-capture-702"
        )));
        when(discrepancyRepository.findByAuthorizationIdIn(any())).thenReturn(List.of(open));
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
        queue(authorization, "100.00", "30.00");
        when(settlementGateway.find(703L)).thenReturn(Optional.of(new ProviderSettlementSnapshot(
            703L,
            103L,
            new Money("100.00"),
            new Money("100.00"),
            Money.ZERO,
            ProviderSettlementStatus.CAPTURED,
            "provider-capture-703"
        )));
        when(discrepancyRepository.findByAuthorizationIdIn(any())).thenReturn(List.of());
        when(discrepancyRepository.saveAndFlush(any(SettlementDiscrepancy.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        SettlementReconciliationReport report = reconciler.scan();

        assertThat(report.detected()).isEqualTo(2);
        ArgumentCaptor<SettlementDiscrepancy> captor = ArgumentCaptor.forClass(
            SettlementDiscrepancy.class
        );
        verify(discrepancyRepository, times(2)).saveAndFlush(captor.capture());
        assertThat(captor.getAllValues())
            .extracting(SettlementDiscrepancy::getType)
            .containsExactlyInAnyOrder(
                SettlementDiscrepancyType.STATUS_MISMATCH,
                SettlementDiscrepancyType.REFUND_AMOUNT_MISMATCH
            );
    }

    @Test
    void syncProviderRepairUsesIdempotencyKeyOnceResolvesAndRequeues() {
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
        verify(settlementGateway, times(1)).synchronize(
            any(SettlementTarget.class),
            eq("repair-704-1")
        );
        verify(workRepository, times(1)).enqueue(eq(704L), any(Instant.class));
    }

    @Test
    void acknowledgeRepairDoesNotMutateProviderAndRequeues() {
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
        verify(workRepository).enqueue(eq(705L), any(Instant.class));
    }

    private void queue(Authorization authorization, String captured, String refunded) {
        when(workRepository.claimDue(
            SettlementReconciler.DEFAULT_BATCH_SIZE,
            NOW,
            SettlementReconciler.DEFAULT_LEASE
        )).thenReturn(List.of(claim(authorization.getId())));
        when(authorizationRepository.findAllById(any()))
            .thenReturn(List.of(authorization));
        when(ledgerRepository.sumSettlementTotalsByAuthorizationIds(any()))
            .thenReturn(List.of(ledgerTotals(authorization.getId(), captured, refunded)));
    }

    private void queueTwice(Authorization authorization, String captured, String refunded) {
        when(workRepository.claimDue(
            SettlementReconciler.DEFAULT_BATCH_SIZE,
            NOW,
            SettlementReconciler.DEFAULT_LEASE
        )).thenReturn(
            List.of(claim(authorization.getId())),
            List.of(claim(authorization.getId()))
        );
        when(authorizationRepository.findAllById(any()))
            .thenReturn(List.of(authorization));
        when(ledgerRepository.sumSettlementTotalsByAuthorizationIds(any()))
            .thenReturn(List.of(ledgerTotals(authorization.getId(), captured, refunded)));
    }

    private SettlementReconciliationWork claim(Long authorizationId) {
        return new SettlementReconciliationWork(
            authorizationId,
            NOW.minusSeconds(1),
            NOW.plus(Duration.ofMinutes(2)),
            1,
            null,
            NOW.minus(Duration.ofDays(1)),
            NOW
        );
    }

    private PaymentLedgerEntryRepository.LedgerTotalsProjection ledgerTotals(
        Long authorizationId,
        String captured,
        String refunded
    ) {
        return new PaymentLedgerEntryRepository.LedgerTotalsProjection() {
            @Override
            public Long getAuthorizationId() {
                return authorizationId;
            }

            @Override
            public BigDecimal getCapturedAmount() {
                return new BigDecimal(captured);
            }

            @Override
            public BigDecimal getRefundedAmount() {
                return new BigDecimal(refunded);
            }
        };
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
}
