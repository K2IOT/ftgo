package net.ftgo.accounting.settlement;

import io.micrometer.core.instrument.MeterRegistry;
import net.ftgo.accounting.domain.Authorization;
import net.ftgo.accounting.domain.AuthorizationStatus;
import net.ftgo.accounting.repository.AuthorizationRepository;
import net.ftgo.common.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class SettlementReconciler {

    private static final EnumSet<SettlementDiscrepancyStatus> ACTIVE_STATUSES = EnumSet.of(
        SettlementDiscrepancyStatus.OPEN,
        SettlementDiscrepancyStatus.ACKNOWLEDGED,
        SettlementDiscrepancyStatus.FAILED
    );

    private final AuthorizationRepository authorizationRepository;
    private final SettlementGateway settlementGateway;
    private final SettlementDiscrepancyRepository discrepancyRepository;
    private final PaymentLedgerEntryRepository ledgerRepository;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    public SettlementReconciler(
        AuthorizationRepository authorizationRepository,
        SettlementGateway settlementGateway,
        SettlementDiscrepancyRepository discrepancyRepository,
        PaymentLedgerEntryRepository ledgerRepository,
        MeterRegistry meterRegistry
    ) {
        this(
            authorizationRepository,
            settlementGateway,
            discrepancyRepository,
            ledgerRepository,
            meterRegistry,
            Clock.systemUTC()
        );
    }

    SettlementReconciler(
        AuthorizationRepository authorizationRepository,
        SettlementGateway settlementGateway,
        SettlementDiscrepancyRepository discrepancyRepository,
        PaymentLedgerEntryRepository ledgerRepository,
        MeterRegistry meterRegistry,
        Clock clock
    ) {
        this.authorizationRepository = authorizationRepository;
        this.settlementGateway = settlementGateway;
        this.discrepancyRepository = discrepancyRepository;
        this.ledgerRepository = ledgerRepository;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
    }

    @Transactional
    public SettlementReconciliationReport scan() {
        Instant now = clock.instant();
        List<Authorization> authorizations = authorizationRepository.findAll();
        Set<String> observedFingerprints = new HashSet<>();
        int detected = 0;

        for (Authorization authorization : authorizations) {
            if (!isSettlementRelevant(authorization.getStatus())) continue;
            List<Observation> observations = inspect(authorization);
            for (Observation observation : observations) {
                observedFingerprints.add(observation.fingerprint());
                upsert(authorization, observation, now);
                detected++;
                meterRegistry.counter(
                    "ftgo.accounting.settlement.discrepancies.detected",
                    "type",
                    observation.type().name()
                ).increment();
            }
        }

        int resolved = 0;
        for (SettlementDiscrepancy discrepancy : discrepancyRepository.findByStatusIn(ACTIVE_STATUSES)) {
            if (!observedFingerprints.contains(discrepancy.getFingerprint())) {
                discrepancy.resolve(now);
                discrepancyRepository.saveAndFlush(discrepancy);
                resolved++;
                meterRegistry.counter(
                    "ftgo.accounting.settlement.discrepancies.resolved",
                    "type",
                    discrepancy.getType().name()
                ).increment();
            }
        }

        meterRegistry.gauge(
            "ftgo.accounting.settlement.discrepancies.current",
            detected
        );
        return new SettlementReconciliationReport(authorizations.size(), detected, resolved);
    }

    @Transactional
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
        if (discrepancy.sameRepairRequest(requestId, action)) {
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
            return discrepancyRepository.saveAndFlush(discrepancy);
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
                if (!inspect(authorization).isEmpty()) {
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
            return discrepancyRepository.saveAndFlush(discrepancy);
        } catch (RuntimeException failure) {
            discrepancy.fail(failure.getMessage(), clock.instant());
            discrepancyRepository.saveAndFlush(discrepancy);
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
        Instant now
    ) {
        Optional<SettlementDiscrepancy> existing = discrepancyRepository.findByFingerprint(
            observation.fingerprint()
        );
        if (existing.isPresent()) {
            SettlementDiscrepancy discrepancy = existing.get();
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
        discrepancyRepository.saveAndFlush(new SettlementDiscrepancy(
            authorization.getId(),
            authorization.getOrderId(),
            observation.type(),
            observation.fingerprint(),
            observation.localState(),
            observation.providerState(),
            observation.details(),
            now
        ));
    }

    private List<Observation> inspect(Authorization authorization) {
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

        LedgerTotals ledger = ledgerTotals(authorization.getId());
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

    private LedgerTotals ledgerTotals(Long authorizationId) {
        BigDecimal captured = BigDecimal.ZERO.setScale(2);
        BigDecimal refunded = BigDecimal.ZERO.setScale(2);
        for (PaymentLedgerEntry entry : ledgerRepository
            .findByAuthorizationIdOrderByOccurredAtAsc(authorizationId)) {
            if (entry.getOperationType() == PaymentLedgerEntry.OperationType.CAPTURE) {
                captured = captured.add(entry.getAmount());
            } else if (entry.getOperationType() == PaymentLedgerEntry.OperationType.REFUND) {
                refunded = refunded.add(entry.getAmount());
            }
        }
        return new LedgerTotals(new Money(captured), new Money(refunded));
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
    }
}