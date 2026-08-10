package net.ftgo.accounting.settlement;

import io.micrometer.core.instrument.MeterRegistry;
import net.ftgo.accounting.domain.Authorization;
import net.ftgo.accounting.domain.AuthorizationStatus;
import net.ftgo.accounting.repository.AuthorizationRepository;
import net.ftgo.common.Money;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SettlementReconciler {

    static final int DEFAULT_BATCH_SIZE = 100;
    static final Duration DEFAULT_LEASE = Duration.ofMinutes(2);
    static final Duration DEFAULT_STEADY_RESCAN = Duration.ofHours(24);
    static final Duration DEFAULT_DISCREPANCY_RESCAN = Duration.ofMinutes(5);

    private static final EnumSet<SettlementDiscrepancyStatus> ACTIVE_STATUSES = EnumSet.of(
        SettlementDiscrepancyStatus.OPEN,
        SettlementDiscrepancyStatus.ACKNOWLEDGED,
        SettlementDiscrepancyStatus.FAILED
    );

    private final AuthorizationRepository authorizationRepository;
    private final SettlementGateway settlementGateway;
    private final SettlementDiscrepancyRepository discrepancyRepository;
    private final PaymentLedgerEntryRepository ledgerRepository;
    private final SettlementReconciliationWorkRepository workRepository;
    private final MeterRegistry meterRegistry;
    private final Clock clock;
    private final AtomicInteger currentDiscrepancies = new AtomicInteger();

    @Autowired
    public SettlementReconciler(
        AuthorizationRepository authorizationRepository,
        SettlementGateway settlementGateway,
        SettlementDiscrepancyRepository discrepancyRepository,
        PaymentLedgerEntryRepository ledgerRepository,
        SettlementReconciliationWorkRepository workRepository,
        MeterRegistry meterRegistry
    ) {
        this(
            authorizationRepository,
            settlementGateway,
            discrepancyRepository,
            ledgerRepository,
            workRepository,
            meterRegistry,
            Clock.systemUTC()
        );
    }

    SettlementReconciler(
        AuthorizationRepository authorizationRepository,
        SettlementGateway settlementGateway,
        SettlementDiscrepancyRepository discrepancyRepository,
        PaymentLedgerEntryRepository ledgerRepository,
        SettlementReconciliationWorkRepository workRepository,
        MeterRegistry meterRegistry,
        Clock clock
    ) {
        this.authorizationRepository = authorizationRepository;
        this.settlementGateway = settlementGateway;
        this.discrepancyRepository = discrepancyRepository;
        this.ledgerRepository = ledgerRepository;
        this.workRepository = workRepository;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
        meterRegistry.gauge(
            "ftgo.accounting.settlement.discrepancies.current",
            currentDiscrepancies
        );
    }

    public SettlementReconciliationReport scan() {
        return scan(
            DEFAULT_BATCH_SIZE,
            DEFAULT_LEASE,
            DEFAULT_STEADY_RESCAN,
            DEFAULT_DISCREPANCY_RESCAN
        );
    }

    public SettlementReconciliationReport scan(
        int batchSize,
        Duration lease,
        Duration steadyRescan,
        Duration discrepancyRescan
    ) {
        Instant now = clock.instant();
        List<SettlementReconciliationWork> claimed = workRepository.claimDue(
            batchSize,
            now,
            lease
        );
        if (claimed.isEmpty()) {
            return new SettlementReconciliationReport(0, 0, 0);
        }

        List<Long> authorizationIds = claimed.stream()
            .map(SettlementReconciliationWork::authorizationId)
            .toList();
        Map<Long, Authorization> authorizations = authorizationRepository
            .findAllById(authorizationIds)
            .stream()
            .collect(Collectors.toMap(Authorization::getId, Function.identity()));
        Map<Long, LedgerTotals> ledgerTotals = loadLedgerTotals(authorizationIds);
        List<SettlementDiscrepancy> existingDiscrepancies = discrepancyRepository
            .findByAuthorizationIdIn(authorizationIds);
        Map<String, SettlementDiscrepancy> discrepanciesByFingerprint = existingDiscrepancies
            .stream()
            .collect(Collectors.toMap(
                SettlementDiscrepancy::getFingerprint,
                Function.identity(),
                (left, right) -> left
            ));

        int detected = 0;
        int resolved = 0;
        for (SettlementReconciliationWork work : claimed) {
            Authorization authorization = authorizations.get(work.authorizationId());
            if (authorization == null) {
                workRepository.recordFailure(
                    work,
                    "Authorization not found: " + work.authorizationId(),
                    clock.instant()
                );
                continue;
            }

            try {
                if (!isSettlementRelevant(authorization.getStatus())) {
                    workRepository.reschedule(
                        work,
                        now.plus(steadyRescan),
                        clock.instant()
                    );
                    continue;
                }

                List<Observation> observations = inspect(
                    authorization,
                    ledgerTotals.getOrDefault(authorization.getId(), LedgerTotals.ZERO)
                );
                Set<String> observedFingerprints = new HashSet<>();
                for (Observation observation : observations) {
                    observedFingerprints.add(observation.fingerprint());
                    upsert(
                        authorization,
                        observation,
                        now,
                        discrepanciesByFingerprint
                    );
                    detected++;
                    meterRegistry.counter(
                        "ftgo.accounting.settlement.discrepancies.detected",
                        "type",
                        observation.type().name()
                    ).increment();
                }

                resolved += resolveUnobserved(
                    authorization.getId(),
                    existingDiscrepancies,
                    observedFingerprints,
                    now
                );
                Duration rescan = observations.isEmpty()
                    ? steadyRescan
                    : discrepancyRescan;
                workRepository.reschedule(
                    work,
                    now.plus(rescan),
                    clock.instant()
                );
            } catch (RuntimeException failure) {
                workRepository.recordFailure(
                    work,
                    failure.getMessage(),
                    clock.instant()
                );
                meterRegistry.counter(
                    "ftgo.accounting.settlement.reconciliation.failures"
                ).increment();
            }
        }

        currentDiscrepancies.set(detected);
        return new SettlementReconciliationReport(claimed.size(), detected, resolved);
    }

    @Transactional(noRollbackFor = RuntimeException.class)
    public SettlementDiscrepancy repair(
        Long discrepancyId,
        SettlementRepairAction action,
        String requestId,
        String reason
    ) {
        if (action == null) throw new IllegalArgumentException("Repair action cannot be null");
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("Repair request ID cannot be blank");
        }
        SettlementDiscrepancy discrepancy = discrepancyRepository.findById(discrepancyId)
            .orElseThrow(() -> new IllegalArgumentException(
                "Settlement discrepancy not found: " + discrepancyId
            ));
        if (discrepancy.sameRepairRequest(requestId, action)
            && (discrepancy.getStatus() == SettlementDiscrepancyStatus.RESOLVED
                || discrepancy.getStatus() == SettlementDiscrepancyStatus.ACKNOWLEDGED)) {
            return discrepancy;
        }

        Instant now = clock.instant();
        if (action == SettlementRepairAction.ACKNOWLEDGE) {
            discrepancy.acknowledge(action, requestId, reason, now);
            meterRegistry.counter(
                "ftgo.accounting.settlement.repairs",
                "action",
                action.name(),
                "result",
                "acknowledged"
            ).increment();
            SettlementDiscrepancy saved = discrepancyRepository.saveAndFlush(discrepancy);
            workRepository.enqueue(discrepancy.getAuthorizationId(), now);
            return saved;
        }

        discrepancy.markRepairing(action, requestId, reason, now);
        discrepancyRepository.saveAndFlush(discrepancy);
        try {
            if (action == SettlementRepairAction.SYNC_PROVIDER_FROM_LOCAL) {
                Authorization authorization = requireAuthorization(discrepancy.getAuthorizationId());
                SettlementDecision decision = settlementGateway.synchronize(
                    target(authorization),
                    requestId
                );
                if (!decision.approved()) {
                    throw new IllegalStateException(
                        "Provider rejected settlement synchronization: " + decision.reason()
                    );
                }
            } else if (action == SettlementRepairAction.VERIFY_RESOLVED) {
                Authorization authorization = requireAuthorization(discrepancy.getAuthorizationId());
                LedgerTotals totals = loadLedgerTotals(List.of(authorization.getId()))
                    .getOrDefault(authorization.getId(), LedgerTotals.ZERO);
                if (!inspect(authorization, totals).isEmpty()) {
                    throw new IllegalStateException(
                        "Settlement discrepancy remains after verification"
                    );
                }
            }
            discrepancy.resolve(clock.instant());
            meterRegistry.counter(
                "ftgo.accounting.settlement.repairs",
                "action",
                action.name(),
                "result",
                "success"
            ).increment();
            SettlementDiscrepancy saved = discrepancyRepository.saveAndFlush(discrepancy);
            workRepository.enqueue(discrepancy.getAuthorizationId(), clock.instant());
            return saved;
        } catch (RuntimeException failure) {
            discrepancy.fail(failure.getMessage(), clock.instant());
            discrepancyRepository.saveAndFlush(discrepancy);
            workRepository.enqueue(discrepancy.getAuthorizationId(), clock.instant());
            meterRegistry.counter(
                "ftgo.accounting.settlement.repairs",
                "action",
                action.name(),
                "result",
                "failure"
            ).increment();
            throw failure;
        }
    }

    @Transactional(readOnly = true)
    public List<SettlementDiscrepancy> findAll() {
        return discrepancyRepository.findAllByOrderByFirstDetectedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<SettlementDiscrepancy> findByAuthorization(Long authorizationId) {
        return discrepancyRepository.findByAuthorizationIdOrderByFirstDetectedAtDesc(
            authorizationId
        );
    }

    private void upsert(
        Authorization authorization,
        Observation observation,
        Instant now,
        Map<String, SettlementDiscrepancy> discrepanciesByFingerprint
    ) {
        SettlementDiscrepancy discrepancy = discrepanciesByFingerprint.get(
            observation.fingerprint()
        );
        if (discrepancy != null) {
            if (discrepancy.refresh(
                observation.localState(),
                observation.providerState(),
                observation.details(),
                now
            )) {
                discrepancyRepository.saveAndFlush(discrepancy);
            }
            return;
        }
        SettlementDiscrepancy created = discrepancyRepository.saveAndFlush(
            new SettlementDiscrepancy(
                authorization.getId(),
                authorization.getOrderId(),
                observation.type(),
                observation.fingerprint(),
                observation.localState(),
                observation.providerState(),
                observation.details(),
                now
            )
        );
        discrepanciesByFingerprint.put(observation.fingerprint(), created);
    }

    private int resolveUnobserved(
        Long authorizationId,
        Collection<SettlementDiscrepancy> existingDiscrepancies,
        Set<String> observedFingerprints,
        Instant now
    ) {
        int resolved = 0;
        for (SettlementDiscrepancy discrepancy : existingDiscrepancies) {
            if (!authorizationId.equals(discrepancy.getAuthorizationId())) continue;
            if (!ACTIVE_STATUSES.contains(discrepancy.getStatus())) continue;
            if (observedFingerprints.contains(discrepancy.getFingerprint())) continue;

            discrepancy.resolve(now);
            discrepancyRepository.saveAndFlush(discrepancy);
            resolved++;
            meterRegistry.counter(
                "ftgo.accounting.settlement.discrepancies.resolved",
                "type",
                discrepancy.getType().name()
            ).increment();
        }
        return resolved;
    }

    private List<Observation> inspect(
        Authorization authorization,
        LedgerTotals ledger
    ) {
        List<Observation> observations = new ArrayList<>();
        SettlementTarget local = target(authorization);
        Optional<ProviderSettlementSnapshot> provider = settlementGateway.find(
            authorization.getId()
        );
        if (provider.isEmpty()) {
            observations.add(observation(
                authorization,
                SettlementDiscrepancyType.PROVIDER_MISSING,
                local.status().name(),
                "MISSING",
                "Provider settlement is missing"
            ));
        } else {
            ProviderSettlementSnapshot snapshot = provider.get();
            if (snapshot.status() != local.status()) {
                observations.add(observation(
                    authorization,
                    SettlementDiscrepancyType.STATUS_MISMATCH,
                    local.status().name(),
                    snapshot.status().name(),
                    "Provider status differs from local authorization status"
                ));
            }
            if (!snapshot.capturedAmount().equals(local.capturedAmount())) {
                observations.add(observation(
                    authorization,
                    SettlementDiscrepancyType.CAPTURE_AMOUNT_MISMATCH,
                    local.capturedAmount().toString(),
                    snapshot.capturedAmount().toString(),
                    "Provider captured amount differs from local captured amount"
                ));
            }
            if (!snapshot.refundedAmount().equals(local.refundedAmount())) {
                observations.add(observation(
                    authorization,
                    SettlementDiscrepancyType.REFUND_AMOUNT_MISMATCH,
                    local.refundedAmount().toString(),
                    snapshot.refundedAmount().toString(),
                    "Provider refunded amount differs from local refunded amount"
                ));
            }
        }

        if (!ledger.captured().equals(local.capturedAmount())
            || !ledger.refunded().equals(local.refundedAmount())) {
            observations.add(observation(
                authorization,
                SettlementDiscrepancyType.LEDGER_MISMATCH,
                "capture=" + local.capturedAmount() + ",refund=" + local.refundedAmount(),
                "capture=" + ledger.captured() + ",refund=" + ledger.refunded(),
                "Immutable ledger totals differ from local authorization state"
            ));
        }
        return observations;
    }

    private Map<Long, LedgerTotals> loadLedgerTotals(Collection<Long> authorizationIds) {
        if (authorizationIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, LedgerTotals> totals = new HashMap<>();
        for (PaymentLedgerEntryRepository.LedgerTotalsProjection projection
            : ledgerRepository.sumSettlementTotalsByAuthorizationIds(authorizationIds)) {
            totals.put(
                projection.getAuthorizationId(),
                new LedgerTotals(
                    money(projection.getCapturedAmount()),
                    money(projection.getRefundedAmount())
                )
            );
        }
        return totals;
    }

    private Money money(BigDecimal amount) {
        return amount == null ? Money.ZERO : new Money(amount);
    }

    private Observation observation(
        Authorization authorization,
        SettlementDiscrepancyType type,
        String localState,
        String providerState,
        String details
    ) {
        return new Observation(
            authorization.getId() + ":" + type.name(),
            type,
            localState,
            providerState,
            details
        );
    }

    private SettlementTarget target(Authorization authorization) {
        ProviderSettlementStatus providerStatus = switch (authorization.getStatus()) {
            case AUTHORIZED, APPROVED -> ProviderSettlementStatus.AUTHORIZED;
            case CAPTURED -> ProviderSettlementStatus.CAPTURED;
            case PARTIALLY_REFUNDED -> ProviderSettlementStatus.PARTIALLY_REFUNDED;
            case REFUNDED -> ProviderSettlementStatus.REFUNDED;
            case VOIDED, REVERSED -> ProviderSettlementStatus.VOIDED;
            case DENIED -> throw new IllegalStateException(
                "Denied authorization has no settlement target"
            );
        };
        Money capturedAmount = switch (providerStatus) {
            case CAPTURED, PARTIALLY_REFUNDED, REFUNDED -> authorization.getAmount();
            case AUTHORIZED, VOIDED -> Money.ZERO;
        };
        return new SettlementTarget(
            authorization.getId(),
            authorization.getOrderId(),
            authorization.getAmount(),
            capturedAmount,
            authorization.getRefundedAmount(),
            providerStatus
        );
    }

    private boolean isSettlementRelevant(AuthorizationStatus status) {
        return status != AuthorizationStatus.DENIED;
    }

    private Authorization requireAuthorization(Long authorizationId) {
        return authorizationRepository.findById(authorizationId)
            .orElseThrow(() -> new IllegalArgumentException(
                "Authorization not found: " + authorizationId
            ));
    }

    private record Observation(
        String fingerprint,
        SettlementDiscrepancyType type,
        String localState,
        String providerState,
        String details
    ) {
    }

    private record LedgerTotals(Money captured, Money refunded) {
        private static final LedgerTotals ZERO = new LedgerTotals(Money.ZERO, Money.ZERO);
    }
}
