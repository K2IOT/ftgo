package net.ftgo.accounting.settlement;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.ftgo.accounting.domain.Authorization;
import net.ftgo.accounting.domain.AuthorizationStatus;
import net.ftgo.accounting.repository.AuthorizationRepository;
import net.ftgo.common.Money;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettlementRepairRetryTest {

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

    @Test
    void failedRepairContinuesWithTheSameIdempotencyKey() {
        Authorization authorization = new Authorization(
            501L,
            101L,
            "authorize-701",
            new Money("60.00"),
            AuthorizationStatus.AUTHORIZED
        );
        ReflectionTestUtils.setField(authorization, "id", 701L);
        authorization.capture("capture-701");

        SettlementDiscrepancy discrepancy = new SettlementDiscrepancy(
            701L,
            101L,
            SettlementDiscrepancyType.PROVIDER_MISSING,
            "701:PROVIDER_MISSING",
            "CAPTURED",
            "MISSING",
            "Provider settlement is missing",
            Instant.parse("2026-07-27T01:00:00Z")
        );
        ReflectionTestUtils.setField(discrepancy, "id", 9001L);

        when(discrepancyRepository.findById(9001L)).thenReturn(Optional.of(discrepancy));
        when(discrepancyRepository.saveAndFlush(discrepancy)).thenReturn(discrepancy);
        when(authorizationRepository.findById(701L)).thenReturn(Optional.of(authorization));
        when(settlementGateway.synchronize(
            any(SettlementTarget.class),
            eq("repair-701-1")
        )).thenThrow(new SettlementGatewayTimeoutException("temporary timeout"))
          .thenReturn(SettlementDecision.approved("provider-repair-701"));

        SettlementReconciler reconciler = new SettlementReconciler(
            authorizationRepository,
            settlementGateway,
            discrepancyRepository,
            ledgerRepository,
            workRepository,
            new SimpleMeterRegistry(),
            Clock.fixed(Instant.parse("2026-07-27T02:00:00Z"), ZoneOffset.UTC)
        );

        assertThatThrownBy(() -> reconciler.repair(
            9001L,
            SettlementRepairAction.SYNC_PROVIDER_FROM_LOCAL,
            "repair-701-1",
            "restore provider state"
        )).isInstanceOf(SettlementGatewayTimeoutException.class);
        assertThat(discrepancy.getStatus()).isEqualTo(SettlementDiscrepancyStatus.FAILED);

        SettlementDiscrepancy repaired = reconciler.repair(
            9001L,
            SettlementRepairAction.SYNC_PROVIDER_FROM_LOCAL,
            "repair-701-1",
            "restore provider state"
        );

        assertThat(repaired.getStatus()).isEqualTo(SettlementDiscrepancyStatus.RESOLVED);
        assertThat(repaired.getRepairRequestId()).isEqualTo("repair-701-1");
        verify(settlementGateway, times(2)).synchronize(
            any(SettlementTarget.class),
            eq("repair-701-1")
        );
        verify(workRepository, times(2)).enqueue(eq(701L), any(Instant.class));
    }
}