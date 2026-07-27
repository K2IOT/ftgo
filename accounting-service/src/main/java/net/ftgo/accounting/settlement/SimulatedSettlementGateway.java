package net.ftgo.accounting.settlement;

import net.ftgo.common.Money;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Component
public class SimulatedSettlementGateway implements SettlementGateway {

    public static final String PROVIDER_DECLINED = "SIMULATED_PROVIDER_DECLINED";
    public static final String PROVIDER_TIMEOUT = "SIMULATED_PROVIDER_TIMEOUT";

    private final SimulatedProviderPaymentRepository paymentRepository;
    private final SimulatedProviderOperationRepository operationRepository;
    private final Set<String> deniedRequestIds;
    private final Set<String> timeoutOnceRequestIds;
    private final Set<String> timeoutAlwaysRequestIds;

    public SimulatedSettlementGateway(
        SimulatedProviderPaymentRepository paymentRepository,
        SimulatedProviderOperationRepository operationRepository,
        @Value("${ftgo.accounting.settlement.deny-request-ids:}") String deniedRequestIds,
        @Value("${ftgo.accounting.settlement.timeout-once-request-ids:}") String timeoutOnceRequestIds,
        @Value("${ftgo.accounting.settlement.timeout-always-request-ids:}") String timeoutAlwaysRequestIds
    ) {
        this.paymentRepository = paymentRepository;
        this.operationRepository = operationRepository;
        this.deniedRequestIds = csv(deniedRequestIds);
        this.timeoutOnceRequestIds = csv(timeoutOnceRequestIds);
        this.timeoutAlwaysRequestIds = csv(timeoutAlwaysRequestIds);
    }

    @Override
    @Transactional(
        propagation = Propagation.REQUIRES_NEW,
        noRollbackFor = SettlementGatewayTimeoutException.class
    )
    public SettlementDecision authorize(
        Long authorizationId,
        Long orderId,
        Money amount,
        String requestId
    ) {
        requirePositive(amount, "Authorization amount");
        return execute(
            authorizationId,
            orderId,
            SimulatedProviderOperation.OperationType.AUTHORIZE,
            amount,
            requestId,
            () -> {
                SimulatedProviderPayment existing = paymentRepository
                    .findByAuthorizationId(authorizationId)
                    .orElse(null);
                if (existing != null) {
                    if (!existing.matchesAuthorization(orderId, amount)) {
                        throw new IllegalArgumentException(
                            "Provider authorization conflicts with existing payment"
                        );
                    }
                    return existing.getProviderReference();
                }
                String reference = providerReference("authorize", authorizationId, requestId);
                paymentRepository.saveAndFlush(new SimulatedProviderPayment(
                    authorizationId,
                    orderId,
                    amount,
                    reference
                ));
                return reference;
            }
        );
    }

    @Override
    @Transactional(
        propagation = Propagation.REQUIRES_NEW,
        noRollbackFor = SettlementGatewayTimeoutException.class
    )
    public SettlementDecision capture(Long authorizationId, Long orderId, String requestId) {
        SimulatedProviderPayment payment = requirePayment(authorizationId, orderId);
        Money amount = new Money(payment.getAuthorizedAmount());
        return execute(
            authorizationId,
            orderId,
            SimulatedProviderOperation.OperationType.CAPTURE,
            amount,
            requestId,
            () -> {
                payment.capture();
                paymentRepository.saveAndFlush(payment);
                return providerReference("capture", authorizationId, requestId);
            }
        );
    }

    @Override
    @Transactional(
        propagation = Propagation.REQUIRES_NEW,
        noRollbackFor = SettlementGatewayTimeoutException.class
    )
    public SettlementDecision voidAuthorization(
        Long authorizationId,
        Long orderId,
        String requestId
    ) {
        SimulatedProviderPayment payment = requirePayment(authorizationId, orderId);
        Money amount = new Money(payment.getAuthorizedAmount());
        return execute(
            authorizationId,
            orderId,
            SimulatedProviderOperation.OperationType.VOID,
            amount,
            requestId,
            () -> {
                payment.voidAuthorization();
                paymentRepository.saveAndFlush(payment);
                return providerReference("void", authorizationId, requestId);
            }
        );
    }

    @Override
    @Transactional(
        propagation = Propagation.REQUIRES_NEW,
        noRollbackFor = SettlementGatewayTimeoutException.class
    )
    public SettlementDecision refund(
        Long authorizationId,
        Long orderId,
        Money amount,
        String requestId
    ) {
        requirePositive(amount, "Refund amount");
        SimulatedProviderPayment payment = requirePayment(authorizationId, orderId);
        return execute(
            authorizationId,
            orderId,
            SimulatedProviderOperation.OperationType.REFUND,
            amount,
            requestId,
            () -> {
                payment.refund(amount);
                paymentRepository.saveAndFlush(payment);
                return providerReference("refund", authorizationId, requestId);
            }
        );
    }

    @Override
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Optional<ProviderSettlementSnapshot> find(Long authorizationId) {
        return paymentRepository.findByAuthorizationId(authorizationId)
            .map(SimulatedProviderPayment::snapshot);
    }

    @Override
    @Transactional(
        propagation = Propagation.REQUIRES_NEW,
        noRollbackFor = SettlementGatewayTimeoutException.class
    )
    public SettlementDecision synchronize(SettlementTarget target, String requestId) {
        return execute(
            target.authorizationId(),
            target.orderId(),
            SimulatedProviderOperation.OperationType.SYNCHRONIZE,
            target.authorizedAmount(),
            requestId,
            () -> {
                String reference = providerReference(
                    "synchronize",
                    target.authorizationId(),
                    requestId
                );
                SimulatedProviderPayment payment = paymentRepository
                    .findByAuthorizationId(target.authorizationId())
                    .orElseGet(() -> new SimulatedProviderPayment(
                        target.authorizationId(),
                        target.orderId(),
                        target.authorizedAmount(),
                        reference
                    ));
                payment.synchronize(target, reference);
                paymentRepository.saveAndFlush(payment);
                return reference;
            }
        );
    }

    private SettlementDecision execute(
        Long authorizationId,
        Long orderId,
        SimulatedProviderOperation.OperationType operationType,
        Money amount,
        String requestId,
        Supplier<String> approvedOperation
    ) {
        requireRequestId(requestId);
        SimulatedProviderOperation existing = operationRepository.findByRequestId(requestId)
            .orElse(null);
        if (existing != null) {
            existing.requireSame(
                authorizationId,
                orderId,
                operationType,
                amount.getAmount()
            );
            if (existing.getOutcome() == SimulatedProviderOperation.Outcome.APPROVED
                || existing.getOutcome() == SimulatedProviderOperation.Outcome.DENIED) {
                return existing.decision();
            }
            if (timeoutAlwaysRequestIds.contains(requestId)) {
                return recordTimeoutAndThrow(existing);
            }
            String reference = approvedOperation.get();
            existing.approve(reference);
            operationRepository.saveAndFlush(existing);
            return SettlementDecision.approved(reference);
        }

        SimulatedProviderOperation operation = new SimulatedProviderOperation(
            requestId,
            authorizationId,
            orderId,
            operationType,
            amount.getAmount()
        );
        if (deniedRequestIds.contains(requestId)) {
            operation.deny(PROVIDER_DECLINED);
            operationRepository.saveAndFlush(operation);
            return SettlementDecision.denied(PROVIDER_DECLINED);
        }
        if (timeoutOnceRequestIds.contains(requestId)
            || timeoutAlwaysRequestIds.contains(requestId)) {
            return recordTimeoutAndThrow(operation);
        }

        String reference = approvedOperation.get();
        operation.approve(reference);
        operationRepository.saveAndFlush(operation);
        return SettlementDecision.approved(reference);
    }

    private SettlementDecision recordTimeoutAndThrow(SimulatedProviderOperation operation) {
        operation.recordTimeout(PROVIDER_TIMEOUT);
        operationRepository.saveAndFlush(operation);
        throw new SettlementGatewayTimeoutException(PROVIDER_TIMEOUT);
    }

    private SimulatedProviderPayment requirePayment(Long authorizationId, Long orderId) {
        SimulatedProviderPayment payment = paymentRepository.findByAuthorizationId(authorizationId)
            .orElseThrow(() -> new IllegalArgumentException(
                "Provider payment not found for authorization " + authorizationId
            ));
        if (!java.util.Objects.equals(payment.getOrderId(), orderId)) {
            throw new IllegalArgumentException(
                "Provider payment does not belong to order " + orderId
            );
        }
        return payment;
    }

    private String providerReference(String operation, Long authorizationId, String requestId) {
        UUID deterministic = UUID.nameUUIDFromBytes(
            requestId.getBytes(StandardCharsets.UTF_8)
        );
        return "sim-" + operation + "-" + authorizationId + "-" + deterministic;
    }

    private void requirePositive(Money amount, String field) {
        if (amount == null || amount.isZero()) {
            throw new IllegalArgumentException(field + " must be positive");
        }
    }

    private void requireRequestId(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("Request ID cannot be blank");
        }
    }

    private static Set<String> csv(String configured) {
        if (configured == null || configured.isBlank()) return Set.of();
        return Arrays.stream(configured.split(","))
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .collect(Collectors.toUnmodifiableSet());
    }
}