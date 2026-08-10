package net.ftgo.accounting.settlement;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.ftgo.accounting.domain.Authorization;
import net.ftgo.accounting.domain.AuthorizationStatus;
import net.ftgo.accounting.repository.AuthorizationRepository;
import net.ftgo.common.Money;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SettlementReconciliationBatchTest {

    private static final Instant NOW = Instant.parse("2026-08-10T03:00:00Z");

    @Test
    void monitorProcessesTwoHundredFiftyDueAuthorizationsAsHundredHundredFifty() {
        AuthorizationRepository authorizationRepository = mock(AuthorizationRepository.class);
        SettlementGateway settlementGateway = mock(SettlementGateway.class);
        SettlementDiscrepancyRepository discrepancyRepository = mock(
            SettlementDiscrepancyRepository.class
        );
        PaymentLedgerEntryRepository ledgerRepository = mock(PaymentLedgerEntryRepository.class);
        SettlementReconciliationWorkRepository workRepository = mock(
            SettlementReconciliationWorkRepository.class
        );
        Map<Long, Authorization> authorizations = authorizations(250);

        when(workRepository.claimDue(eq(100), eq(NOW), eq(Duration.ofMinutes(2))))
            .thenReturn(claims(1, 100), claims(101, 100), claims(201, 50));
        when(authorizationRepository.findAllById(any())).thenAnswer(invocation -> {
            Iterable<Long> ids = invocation.getArgument(0);
            List<Authorization> batch = new ArrayList<>();
            ids.forEach(id -> batch.add(authorizations.get(id)));
            return batch;
        });
        when(ledgerRepository.sumSettlementTotalsByAuthorizationIds(any()))
            .thenReturn(List.of());
        when(discrepancyRepository.findByAuthorizationIdIn(any()))
            .thenReturn(List.of());
        when(settlementGateway.find(anyLong())).thenAnswer(invocation -> {
            Long authorizationId = invocation.getArgument(0);
            Authorization authorization = authorizations.get(authorizationId);
            return java.util.Optional.of(new ProviderSettlementSnapshot(
                authorizationId,
                authorization.getOrderId(),
                authorization.getAmount(),
                Money.ZERO,
                Money.ZERO,
                ProviderSettlementStatus.AUTHORIZED,
                "provider-" + authorizationId
            ));
        });
        when(authorizationRepository.findAll()).thenThrow(new AssertionError(
            "Settlement reconciliation must not perform a full authorization table scan"
        ));

        SettlementReconciler reconciler = new SettlementReconciler(
            authorizationRepository,
            settlementGateway,
            discrepancyRepository,
            ledgerRepository,
            workRepository,
            new SimpleMeterRegistry(),
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
        SettlementReconciliationMonitor monitor = new SettlementReconciliationMonitor(reconciler);

        assertThat(monitor.reconcile().inspected()).isEqualTo(100);
        assertThat(monitor.reconcile().inspected()).isEqualTo(100);
        assertThat(monitor.reconcile().inspected()).isEqualTo(50);

        verify(workRepository, times(3)).claimDue(
            100,
            NOW,
            Duration.ofMinutes(2)
        );
        verify(authorizationRepository, never()).findAll();
        verify(ledgerRepository, times(3)).sumSettlementTotalsByAuthorizationIds(any());
        verify(ledgerRepository, never()).findByAuthorizationIdOrderByOccurredAtAsc(anyLong());
        verify(workRepository, times(250)).reschedule(
            any(SettlementReconciliationWork.class),
            eq(NOW.plus(Duration.ofHours(24))),
            eq(NOW)
        );
    }

    private Map<Long, Authorization> authorizations(int count) {
        Map<Long, Authorization> result = new HashMap<>();
        for (long id = 1; id <= count; id++) {
            Authorization authorization = new Authorization(
                501L,
                1_000L + id,
                "authorize-" + id,
                new Money("10.00"),
                AuthorizationStatus.AUTHORIZED
            );
            ReflectionTestUtils.setField(authorization, "id", id);
            result.put(id, authorization);
        }
        return result;
    }

    private List<SettlementReconciliationWork> claims(long firstId, int count) {
        List<SettlementReconciliationWork> result = new ArrayList<>();
        for (long id = firstId; id < firstId + count; id++) {
            result.add(new SettlementReconciliationWork(
                id,
                NOW.minusSeconds(1),
                NOW.plus(Duration.ofMinutes(2)),
                1,
                null,
                NOW.minus(Duration.ofDays(1)),
                NOW
            ));
        }
        return result;
    }
}
