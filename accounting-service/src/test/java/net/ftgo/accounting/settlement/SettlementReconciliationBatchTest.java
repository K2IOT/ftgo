package net.ftgo.accounting.settlement;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.ftgo.accounting.repository.AuthorizationRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SettlementReconciliationBatchTest {

    @Test
    void monitorDoesNotPerformFullTableAuthorizationScan() {
        AuthorizationRepository authorizationRepository = mock(AuthorizationRepository.class);
        SettlementGateway settlementGateway = mock(SettlementGateway.class);
        SettlementDiscrepancyRepository discrepancyRepository = mock(
            SettlementDiscrepancyRepository.class
        );
        PaymentLedgerEntryRepository ledgerRepository = mock(PaymentLedgerEntryRepository.class);

        when(authorizationRepository.findAll()).thenThrow(new AssertionError(
            "Settlement reconciliation must not perform a full authorization table scan"
        ));

        SettlementReconciler reconciler = new SettlementReconciler(
            authorizationRepository,
            settlementGateway,
            discrepancyRepository,
            ledgerRepository,
            new SimpleMeterRegistry(),
            Clock.fixed(Instant.parse("2026-08-10T03:00:00Z"), ZoneOffset.UTC)
        );
        SettlementReconciliationMonitor monitor = new SettlementReconciliationMonitor(reconciler);

        monitor.reconcile();
    }
}
