package net.ftgo.accounting.settlement;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Objects;

@Service
public class PaymentLedgerService {

    private final PaymentLedgerEntryRepository repository;

    public PaymentLedgerService(PaymentLedgerEntryRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public PaymentLedgerEntry append(Long accountId, Long orderId, Long authorizationId,
                                     PaymentLedgerEntry.OperationType operationType,
                                     String requestId, BigDecimal amount,
                                     String providerReference) {
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("Request ID cannot be blank");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        return repository.findByRequestId(requestId)
            .map(existing -> requireSameOperation(
                existing,
                accountId,
                orderId,
                authorizationId,
                operationType,
                amount,
                providerReference
            ))
            .orElseGet(() -> repository.saveAndFlush(new PaymentLedgerEntry(
                accountId,
                orderId,
                authorizationId,
                operationType,
                requestId,
                amount,
                providerReference
            )));
    }

    private PaymentLedgerEntry requireSameOperation(PaymentLedgerEntry existing,
                                                     Long accountId, Long orderId,
                                                     Long authorizationId,
                                                     PaymentLedgerEntry.OperationType operationType,
                                                     BigDecimal amount,
                                                     String providerReference) {
        if (!existing.getAccountId().equals(accountId)
            || !Objects.equals(existing.getOrderId(), orderId)
            || !Objects.equals(existing.getAuthorizationId(), authorizationId)
            || existing.getOperationType() != operationType
            || existing.getAmount().compareTo(amount) != 0
            || !Objects.equals(existing.getProviderReference(), providerReference)) {
            throw new IllegalArgumentException(
                "Payment ledger request ID conflict: " + existing.getRequestId()
            );
        }
        return existing;
    }
}