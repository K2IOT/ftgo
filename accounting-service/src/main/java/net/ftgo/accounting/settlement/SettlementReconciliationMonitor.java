package net.ftgo.accounting.settlement;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    prefix = "ftgo.accounting.settlement.reconciliation",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class SettlementReconciliationMonitor {

    private final SettlementReconciler reconciler;

    public SettlementReconciliationMonitor(SettlementReconciler reconciler) {
        this.reconciler = reconciler;
    }

    @Scheduled(
        fixedDelayString = "${ftgo.accounting.settlement.reconciliation.interval-ms:60000}",
        initialDelayString = "${ftgo.accounting.settlement.reconciliation.initial-delay-ms:30000}"
    )
    public SettlementReconciliationReport reconcile() {
        return reconciler.scan();
    }
}