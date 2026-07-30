package net.ftgo.accounting.settlement;

import net.ftgo.accounting.domain.Account;
import net.ftgo.accounting.domain.Authorization;
import net.ftgo.accounting.domain.AuthorizationStatus;
import net.ftgo.accounting.messaging.DomainEventPublisher;
import net.ftgo.accounting.messaging.PaymentRefundedEvent;
import net.ftgo.accounting.repository.AccountRepository;
import net.ftgo.common.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class ManualPaymentSettlementService {

    private final AccountRepository accountRepository;
    private final SettlementGateway settlementGateway;
    private final PaymentLedgerService ledgerService;
    private final DomainEventPublisher eventPublisher;

    public ManualPaymentSettlementService(
        AccountRepository accountRepository,
        SettlementGateway settlementGateway,
        PaymentLedgerService ledgerService,
        DomainEventPublisher eventPublisher
    ) {
        this.accountRepository = accountRepository;
        this.settlementGateway = settlementGateway;
        this.ledgerService = ledgerService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public RefundResult refund(
        Long authorizationId,
        Money amount,
        String reason,
        String requestId
    ) {
        if (authorizationId == null) {
            throw new IllegalArgumentException("Authorization ID cannot be null");
        }
        if (amount == null || amount.isZero()) {
            throw new IllegalArgumentException("Refund amount must be positive");
        }
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("Refund request ID cannot be blank");
        }

        Account account = accountRepository.findByAuthorizationId(authorizationId)
            .orElseThrow(() -> new IllegalArgumentException(
                "Account not found for authorization " + authorizationId
            ));
        Authorization authorization = account.findAuthorizationById(authorizationId);
        if (authorization == null) {
            throw new IllegalArgumentException(
                "Authorization not found: " + authorizationId
            );
        }

        boolean replay = authorization.getRefunds().stream()
            .anyMatch(refund -> refund.getRequestId().equals(requestId));
        if (!replay && authorization.getRefundedAmount().add(amount)
            .isGreaterThan(authorization.getAmount())) {
            throw new RefundAmountExceedsCapturedException();
        }

        SettlementDecision settlement = settlementGateway.refund(
            authorizationId,
            authorization.getOrderId(),
            amount,
            requestId
        );
        if (!settlement.approved()) {
            throw new IllegalStateException(
                "Provider rejected refund: " + settlement.reason()
            );
        }

        boolean changed = account.refundPayment(
            authorization.getOrderId(),
            authorizationId,
            amount,
            reason,
            requestId
        );
        if (changed) {
            accountRepository.saveAndFlush(account);
        }
        ledgerService.append(
            account.getId(),
            authorization.getOrderId(),
            authorizationId,
            PaymentLedgerEntry.OperationType.REFUND,
            requestId,
            amount.getAmount(),
            settlement.providerReference()
        );
        if (changed) {
            eventPublisher.publishAccountEvent(
                account.getId(),
                account.getVersion(),
                new PaymentRefundedEvent(
                    account.getId(),
                    authorization.getOrderId(),
                    authorizationId,
                    reason,
                    requestId,
                    LocalDateTime.now()
                )
            );
        }
        return new RefundResult(
            authorizationId,
            authorization.getOrderId(),
            authorization.getStatus(),
            authorization.getRefundedAmount(),
            authorization.getRefundableAmount(),
            requestId,
            settlement.providerReference()
        );
    }

    public record RefundResult(
        Long authorizationId,
        Long orderId,
        AuthorizationStatus status,
        Money refundedAmount,
        Money refundableAmount,
        String requestId,
        String providerReference
    ) {
    }
}
