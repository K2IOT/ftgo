package net.ftgo.accounting.reconciliation;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import net.ftgo.accounting.domain.AuthorizationStatus;
import net.ftgo.accounting.payment.PaymentProvider;
import net.ftgo.accounting.payment.PaymentProviderCharge;
import net.ftgo.accounting.repository.AuthorizationRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
public class PaymentReconciliationScheduler {

    private static final List<AuthorizationStatus> RECONCILABLE_STATUSES = List.of(
        AuthorizationStatus.APPROVED,
        AuthorizationStatus.AUTHORIZED,
        AuthorizationStatus.CAPTURED
    );

    private final AuthorizationRepository authorizationRepository;
    private final PaymentReconciliationService reconciliationService;
    private final PaymentProvider paymentProvider;
    private final Counter failures;

    public PaymentReconciliationScheduler(
        AuthorizationRepository authorizationRepository,
        PaymentReconciliationService reconciliationService,
        PaymentProvider paymentProvider,
        MeterRegistry meterRegistry
    ) {
        this.authorizationRepository = authorizationRepository;
        this.reconciliationService = reconciliationService;
        this.paymentProvider = paymentProvider;
        this.failures = Counter.builder(
            "accounting_payment_reconciliation_failures_total")
            .description("Unexpected payment reconciliation failures")
            .register(meterRegistry);
    }

    @Scheduled(
        fixedDelayString = "${ftgo.accounting.reconciliation-fixed-delay-ms:60000}",
        initialDelayString = "${ftgo.accounting.reconciliation-initial-delay-ms:30000}"
    )
    public void reconcilePending() {
        authorizationRepository
            .findTop100ByStatusInOrderByCreatedAtAsc(RECONCILABLE_STATUSES)
            .forEach(authorization -> {
                try {
                    reconciliationService.reconcileAuthorization(
                        authorization.getProviderAuthorizationId()
                    );
                } catch (RuntimeException e) {
                    failures.increment();
                }
            });

        for (PaymentProviderCharge charge : paymentProvider.listRecentCharges(
            Instant.now().minus(24, ChronoUnit.HOURS)
        )) {
            try {
                if (authorizationRepository.findByProviderAuthorizationId(
                    charge.providerAuthorizationId()
                ).isEmpty()) {
                    reconciliationService.reconcileUnknownCharge(charge);
                }
            } catch (RuntimeException e) {
                failures.increment();
            }
        }
    }
}
