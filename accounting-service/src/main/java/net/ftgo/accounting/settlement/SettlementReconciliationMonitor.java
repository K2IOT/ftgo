package net.ftgo.accounting.settlement;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConditionalOnProperty(
    prefix = "ftgo.accounting.settlement.reconciliation",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class SettlementReconciliationMonitor {

    private final SettlementReconciler reconciler;
    private final int batchSize;
    private final Duration lease;
    private final Duration steadyRescan;
    private final Duration discrepancyRescan;

    @Autowired
    public SettlementReconciliationMonitor(
        SettlementReconciler reconciler,
        @Value("${ftgo.accounting.settlement.reconciliation.batch-size:100}") int batchSize,
        @Value("${ftgo.accounting.settlement.reconciliation.lease:PT2M}") String lease,
        @Value("${ftgo.accounting.settlement.reconciliation.steady-rescan:PT24H}") String steadyRescan,
        @Value("${ftgo.accounting.settlement.reconciliation.discrepancy-rescan:PT5M}") String discrepancyRescan
    ) {
        this(
            reconciler,
            batchSize,
            Duration.parse(lease),
            Duration.parse(steadyRescan),
            Duration.parse(discrepancyRescan)
        );
    }

    SettlementReconciliationMonitor(SettlementReconciler reconciler) {
        this(
            reconciler,
            SettlementReconciler.DEFAULT_BATCH_SIZE,
            SettlementReconciler.DEFAULT_LEASE,
            SettlementReconciler.DEFAULT_STEADY_RESCAN,
            SettlementReconciler.DEFAULT_DISCREPANCY_RESCAN
        );
    }

    SettlementReconciliationMonitor(
        SettlementReconciler reconciler,
        int batchSize,
        Duration lease,
        Duration steadyRescan,
        Duration discrepancyRescan
    ) {
        this.reconciler = reconciler;
        this.batchSize = batchSize;
        this.lease = lease;
        this.steadyRescan = steadyRescan;
        this.discrepancyRescan = discrepancyRescan;
    }

    @Scheduled(
        fixedDelayString = "${ftgo.accounting.settlement.reconciliation.interval-ms:60000}",
        initialDelayString = "${ftgo.accounting.settlement.reconciliation.initial-delay-ms:30000}"
    )
    public SettlementReconciliationReport reconcile() {
        return reconciler.scan(batchSize, lease, steadyRescan, discrepancyRescan);
    }
}
