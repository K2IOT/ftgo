package net.ftgo.accounting.messaging;

import io.eventuate.tram.commands.consumer.CommandHandlers;
import io.eventuate.tram.commands.consumer.CommandMessage;
import io.eventuate.tram.messaging.common.Message;
import io.eventuate.tram.sagas.participant.SagaCommandHandlersBuilder;
import net.ftgo.accounting.domain.Account;
import net.ftgo.accounting.domain.Authorization;
import net.ftgo.accounting.domain.AuthorizationStatus;
import net.ftgo.accounting.payment.PaymentAuthorizationDecision;
import net.ftgo.accounting.payment.PaymentAuthorizationGateway;
import net.ftgo.accounting.repository.AccountRepository;
import net.ftgo.accounting.settlement.PaymentLedgerEntry;
import net.ftgo.accounting.settlement.PaymentLedgerService;
import net.ftgo.accounting.settlement.SettlementDecision;
import net.ftgo.accounting.settlement.SettlementGateway;
import net.ftgo.accounting.settlement.SettlementReconciliationWorkRepository;
import net.ftgo.common.Money;
import net.ftgo.common.channels.ChannelNames;
import net.ftgo.common.messaging.IdempotentCommandExecutor;
import net.ftgo.common.orderflow.commands.AuthorizeCardCommand;
import net.ftgo.common.orderflow.commands.CaptureAuthorizationCommand;
import net.ftgo.common.orderflow.commands.RefundPaymentCommand;
import net.ftgo.common.orderflow.commands.ReverseAuthorizationCommand;
import net.ftgo.common.orderflow.commands.ReviseAuthorizationCommand;
import net.ftgo.common.orderflow.commands.VoidAuthorizationCommand;
import net.ftgo.common.orderflow.replies.AuthorizationRevised;
import net.ftgo.common.orderflow.replies.AuthorizationVoided;
import net.ftgo.common.orderflow.replies.CardAuthorizationDenied;
import net.ftgo.common.orderflow.replies.CardAuthorized;
import net.ftgo.common.orderflow.replies.PaymentCaptured;
import net.ftgo.common.orderflow.replies.PaymentRefunded;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;

import static io.eventuate.tram.commands.consumer.CommandHandlerReplyBuilder.withFailure;
import static io.eventuate.tram.commands.consumer.CommandHandlerReplyBuilder.withSuccess;

@Component
public class AccountingServiceCommandHandlers {

    private static final String CONSUMER_NAME = "accounting-service";

    private final AccountRepository accountRepository;
    private final DomainEventPublisher eventPublisher;
    private final PaymentAuthorizationGateway paymentAuthorizationGateway;
    private final SettlementGateway settlementGateway;
    private final PaymentLedgerService paymentLedgerService;
    private final SettlementReconciliationWorkRepository reconciliationWorkRepository;
    private final IdempotentCommandExecutor idempotentCommandExecutor;

    public AccountingServiceCommandHandlers(
        AccountRepository accountRepository,
        DomainEventPublisher eventPublisher,
        PaymentAuthorizationGateway paymentAuthorizationGateway,
        SettlementGateway settlementGateway,
        PaymentLedgerService paymentLedgerService,
        SettlementReconciliationWorkRepository reconciliationWorkRepository,
        IdempotentCommandExecutor idempotentCommandExecutor
    ) {
        this.accountRepository = accountRepository;
        this.eventPublisher = eventPublisher;
        this.paymentAuthorizationGateway = paymentAuthorizationGateway;
        this.settlementGateway = settlementGateway;
        this.paymentLedgerService = paymentLedgerService;
        this.reconciliationWorkRepository = reconciliationWorkRepository;
        this.idempotentCommandExecutor = idempotentCommandExecutor;
    }

    public CommandHandlers commandHandlers() {
        return SagaCommandHandlersBuilder
            .fromChannel(ChannelNames.ACCOUNTING_SERVICE_COMMAND_CHANNEL)
            .onMessage(AuthorizeCardCommand.class, this::handleAuthorizeCard)
            .onMessage(CaptureAuthorizationCommand.class, this::handleCaptureAuthorization)
            .onMessage(VoidAuthorizationCommand.class, this::handleVoidAuthorization)
            .onMessage(RefundPaymentCommand.class, this::handleRefundPayment)
            .onMessage(ReverseAuthorizationCommand.class, this::handleReverseAuthorization)
            .onMessage(ReviseAuthorizationCommand.class, this::handleReviseAuthorization)
            .build();
    }

    public Message handleAuthorizeCard(CommandMessage<AuthorizeCardCommand> message) {
        return idempotentCommandExecutor.execute(
            CONSUMER_NAME,
            message,
            () -> authorizeCardOnce(message.getCommand())
        );
    }

    public Message handleCaptureAuthorization(CommandMessage<CaptureAuthorizationCommand> message) {
        return idempotentCommandExecutor.execute(
            CONSUMER_NAME,
            message,
            () -> captureAuthorizationOnce(message.getCommand())
        );
    }

    public Message handleVoidAuthorization(CommandMessage<VoidAuthorizationCommand> message) {
        return idempotentCommandExecutor.execute(
            CONSUMER_NAME,
            message,
            () -> voidAuthorizationOnce(message.getCommand())
        );
    }

    public Message handleRefundPayment(CommandMessage<RefundPaymentCommand> message) {
        return idempotentCommandExecutor.execute(
            CONSUMER_NAME,
            message,
            () -> refundPaymentOnce(message.getCommand())
        );
    }

    public Message handleReverseAuthorization(CommandMessage<ReverseAuthorizationCommand> message) {
        return idempotentCommandExecutor.execute(
            CONSUMER_NAME,
            message,
            () -> reverseAuthorizationOnce(message.getCommand())
        );
    }

    public Message handleReviseAuthorization(CommandMessage<ReviseAuthorizationCommand> message) {
        return idempotentCommandExecutor.execute(
            CONSUMER_NAME,
            message,
            () -> reviseAuthorizationOnce(message.getCommand())
        );
    }

    private Message authorizeCardOnce(AuthorizeCardCommand command) {
        try {
            PaymentAuthorizationDecision authorizationDecision = paymentAuthorizationGateway.authorize(
                command.getPaymentToken(),
                command.getAmount()
            );
            if (!authorizationDecision.approved()) {
                return withFailure(new CardAuthorizationDenied(
                    command.getOrderId(),
                    authorizationDecision.reason()
                ));
            }

            Account account = accountRepository.findByConsumerId(command.getConsumerId())
                .orElseGet(() -> accountRepository.saveAndFlush(new Account(command.getConsumerId())));

            Authorization authorization = account.findAuthorizationByRequestId(command.getRequestId());
            boolean created = authorization == null;
            if (authorization != null
                && command.getOrderId() != null
                && !authorization.matches(command.getOrderId(), command.getAmount())) {
                return withFailure(new CardAuthorizationDenied(
                    command.getOrderId(),
                    "Request ID conflict"
                ));
            }
            if (authorization == null) {
                authorization = command.getOrderId() == null
                    ? account.authorize(command.getRequestId(), command.getAmount())
                    : account.authorize(
                        command.getOrderId(),
                        command.getRequestId(),
                        command.getAmount()
                    );
            }

            account = accountRepository.saveAndFlush(account);
            authorization = account.findAuthorizationByRequestId(command.getRequestId());
            if (authorization == null || authorization.getId() == null) {
                throw new IllegalStateException(
                    "Persisted authorization is missing for request " + command.getRequestId()
                );
            }

            SettlementDecision settlement = settlementGateway.authorize(
                authorization.getId(),
                command.getOrderId(),
                authorization.getAmount(),
                command.getRequestId()
            );
            if (!settlement.approved()) {
                throw new IllegalStateException(
                    "Provider authorization state was rejected: " + settlement.reason()
                );
            }
            paymentLedgerService.append(
                account.getId(),
                command.getOrderId(),
                authorization.getId(),
                PaymentLedgerEntry.OperationType.AUTHORIZE,
                command.getRequestId(),
                authorization.getAmount().getAmount(),
                settlement.providerReference()
            );
            enqueueReconciliation(authorization.getId());

            if (created) {
                eventPublisher.publishAccountEvent(
                    account.getId(),
                    account.getVersion(),
                    new CardAuthorizedEvent(
                        account.getId(),
                        authorization.getId(),
                        authorization.getRequestId(),
                        authorization.getAmount().getAmount(),
                        authorization.getCreatedAt()
                    )
                );
            }
            return withSuccess(new CardAuthorized(authorization.getId(), command.getOrderId()));
        } catch (IllegalArgumentException e) {
            if (command.getOrderId() != null) {
                return withFailure(new CardAuthorizationDenied(command.getOrderId(), e.getMessage()));
            }
            return withFailure(e.getMessage());
        }
    }

    private Message captureAuthorizationOnce(CaptureAuthorizationCommand command) {
        try {
            Account account = requireAccount(command.getAuthorizationId());
            Authorization authorization = requireAuthorization(account, command.getAuthorizationId());
            SettlementDecision settlement = settlementGateway.capture(
                command.getAuthorizationId(),
                command.getOrderId(),
                command.getRequestId()
            );
            if (!settlement.approved()) return withFailure(settlement.reason());

            boolean changed = account.captureAuthorization(
                command.getOrderId(),
                command.getAuthorizationId(),
                command.getRequestId()
            );
            if (changed) accountRepository.saveAndFlush(account);
            paymentLedgerService.append(
                account.getId(),
                command.getOrderId(),
                command.getAuthorizationId(),
                PaymentLedgerEntry.OperationType.CAPTURE,
                command.getRequestId(),
                authorization.getAmount().getAmount(),
                settlement.providerReference()
            );
            enqueueReconciliation(command.getAuthorizationId());
            if (changed) {
                eventPublisher.publishAccountEvent(
                    account.getId(),
                    account.getVersion(),
                    new PaymentCapturedEvent(
                        account.getId(),
                        command.getOrderId(),
                        command.getAuthorizationId(),
                        command.getRequestId(),
                        LocalDateTime.now()
                    )
                );
            }
            return withSuccess(new PaymentCaptured(
                command.getAuthorizationId(),
                command.getAuthorizationId(),
                command.getOrderId()
            ));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return withFailure(e.getMessage());
        }
    }

    private Message voidAuthorizationOnce(VoidAuthorizationCommand command) {
        try {
            Account account = requireAccount(command.getAuthorizationId());
            Authorization authorization = requireAuthorization(account, command.getAuthorizationId());
            SettlementDecision settlement = settlementGateway.voidAuthorization(
                command.getAuthorizationId(),
                command.getOrderId(),
                command.getRequestId()
            );
            if (!settlement.approved()) return withFailure(settlement.reason());

            boolean changed = account.voidAuthorization(
                command.getOrderId(),
                command.getAuthorizationId(),
                command.getReason(),
                command.getRequestId()
            );
            if (changed) accountRepository.saveAndFlush(account);
            paymentLedgerService.append(
                account.getId(),
                command.getOrderId(),
                command.getAuthorizationId(),
                PaymentLedgerEntry.OperationType.VOID,
                command.getRequestId(),
                authorization.getAmount().getAmount(),
                settlement.providerReference()
            );
            enqueueReconciliation(command.getAuthorizationId());
            if (changed) {
                eventPublisher.publishAccountEvent(
                    account.getId(),
                    account.getVersion(),
                    new AuthorizationVoidedEvent(
                        account.getId(),
                        command.getOrderId(),
                        command.getAuthorizationId(),
                        command.getReason(),
                        command.getRequestId(),
                        LocalDateTime.now()
                    )
                );
            }
            return withSuccess(new AuthorizationVoided(
                command.getAuthorizationId(),
                command.getOrderId()
            ));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return withFailure(e.getMessage());
        }
    }

    private Message refundPaymentOnce(RefundPaymentCommand command) {
        try {
            Account account = requireAccount(command.getCaptureId());
            SettlementDecision settlement = settlementGateway.refund(
                command.getCaptureId(),
                command.getOrderId(),
                command.getAmount(),
                command.getRequestId()
            );
            if (!settlement.approved()) return withFailure(settlement.reason());

            boolean changed = account.refundPayment(
                command.getOrderId(),
                command.getCaptureId(),
                command.getAmount(),
                command.getReason(),
                command.getRequestId()
            );
            if (changed) accountRepository.saveAndFlush(account);
            paymentLedgerService.append(
                account.getId(),
                command.getOrderId(),
                command.getCaptureId(),
                PaymentLedgerEntry.OperationType.REFUND,
                command.getRequestId(),
                command.getAmount().getAmount(),
                settlement.providerReference()
            );
            enqueueReconciliation(command.getCaptureId());
            if (changed) {
                eventPublisher.publishAccountEvent(
                    account.getId(),
                    account.getVersion(),
                    new PaymentRefundedEvent(
                        account.getId(),
                        command.getOrderId(),
                        command.getCaptureId(),
                        command.getReason(),
                        command.getRequestId(),
                        LocalDateTime.now()
                    )
                );
            }
            return withSuccess(new PaymentRefunded(
                command.getCaptureId(),
                command.getCaptureId(),
                command.getOrderId()
            ));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return withFailure(e.getMessage());
        }
    }

    private Message reverseAuthorizationOnce(ReverseAuthorizationCommand command) {
        try {
            Account account = accountRepository.findByConsumerId(command.getConsumerId())
                .orElseThrow(() -> new IllegalArgumentException(
                    "Account not found for consumer " + command.getConsumerId()
                ));
            Authorization authorization = requireAuthorization(
                account,
                command.getAuthorizationId()
            );

            if (command.getOrderId() == null || command.getRequestId() == null) {
                account.reverseAuthorization(command.getAuthorizationId());
                accountRepository.saveAndFlush(account);
                enqueueReconciliation(command.getAuthorizationId());
                eventPublisher.publishAccountEvent(
                    account.getId(),
                    account.getVersion(),
                    new CardReversed(
                        account.getId(),
                        command.getAuthorizationId(),
                        LocalDateTime.now()
                    )
                );
                return withSuccess(new AuthorizationReversed(command.getAuthorizationId()));
            }

            if (authorization.getStatus() == AuthorizationStatus.AUTHORIZED
                || authorization.getStatus() == AuthorizationStatus.APPROVED) {
                SettlementDecision settlement = settlementGateway.voidAuthorization(
                    authorization.getId(),
                    command.getOrderId(),
                    command.getRequestId()
                );
                if (!settlement.approved()) return withFailure(settlement.reason());

                boolean changed = account.voidAuthorization(
                    command.getOrderId(),
                    authorization.getId(),
                    "ORDER_CANCELLED",
                    command.getRequestId()
                );
                if (changed) accountRepository.saveAndFlush(account);
                paymentLedgerService.append(
                    account.getId(),
                    command.getOrderId(),
                    authorization.getId(),
                    PaymentLedgerEntry.OperationType.VOID,
                    command.getRequestId(),
                    authorization.getAmount().getAmount(),
                    settlement.providerReference()
                );
                enqueueReconciliation(authorization.getId());
                if (changed) {
                    eventPublisher.publishAccountEvent(
                        account.getId(),
                        account.getVersion(),
                        new AuthorizationVoidedEvent(
                            account.getId(),
                            command.getOrderId(),
                            authorization.getId(),
                            "ORDER_CANCELLED",
                            command.getRequestId(),
                            LocalDateTime.now()
                        )
                    );
                }
                return withSuccess(new AuthorizationReversed(authorization.getId()));
            }

            if (authorization.getStatus() == AuthorizationStatus.CAPTURED
                || authorization.getStatus() == AuthorizationStatus.PARTIALLY_REFUNDED) {
                Money remaining = authorization.getRefundableAmount();
                SettlementDecision settlement = settlementGateway.refund(
                    authorization.getId(),
                    command.getOrderId(),
                    remaining,
                    command.getRequestId()
                );
                if (!settlement.approved()) return withFailure(settlement.reason());

                boolean changed = account.refundPayment(
                    command.getOrderId(),
                    authorization.getId(),
                    remaining,
                    "ORDER_CANCELLED",
                    command.getRequestId()
                );
                if (changed) accountRepository.saveAndFlush(account);
                paymentLedgerService.append(
                    account.getId(),
                    command.getOrderId(),
                    authorization.getId(),
                    PaymentLedgerEntry.OperationType.REFUND,
                    command.getRequestId(),
                    remaining.getAmount(),
                    settlement.providerReference()
                );
                enqueueReconciliation(authorization.getId());
                if (changed) {
                    eventPublisher.publishAccountEvent(
                        account.getId(),
                        command.getOrderId(),
                        authorization.getId(),
                        "ORDER_CANCELLED",
                        command.getRequestId(),
                        LocalDateTime.now()
                    )
                );
                return withSuccess(new AuthorizationReversed(authorization.getId()));
            }

            if (authorization.getStatus() == AuthorizationStatus.VOIDED
                || authorization.getStatus() == AuthorizationStatus.REVERSED
                || authorization.getStatus() == AuthorizationStatus.REFUNDED) {
                return withSuccess(new AuthorizationReversed(authorization.getId()));
            }

            return withFailure(
                "Cannot settle cancellation in authorization state "
                    + authorization.getStatus()
            );
        } catch (IllegalArgumentException | IllegalStateException e) {
            return withFailure(e.getMessage());
        }
    }

    private Message reviseAuthorizationOnce(ReviseAuthorizationCommand command) {
        try {
            Account account = accountRepository.findByConsumerId(command.getConsumerId())
                .orElseThrow(() -> new IllegalArgumentException(
                    "Account not found for consumer " + command.getConsumerId()
                ));
            Authorization revised = account.reviseAuthorization(
                command.getAuthorizationId(),
                new Money(command.getNewAmount()),
                command.getRequestId()
            );
            accountRepository.save(account);
            return withSuccess(new AuthorizationRevised(revised.getId()));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return withFailure(e.getMessage());
        }
    }

    private void enqueueReconciliation(Long authorizationId) {
        reconciliationWorkRepository.enqueue(authorizationId, Instant.now());
    }

    private Account requireAccount(Long authorizationId) {
        return accountRepository.findByAuthorizationId(authorizationId)
            .orElseThrow(() -> new IllegalArgumentException(
                "Account not found for authorization " + authorizationId
            ));
    }

    private Authorization requireAuthorization(Account account, Long authorizationId) {
        Authorization authorization = account.findAuthorizationById(authorizationId);
        if (authorization == null) {
            throw new IllegalArgumentException(
                "Authorization with ID " + authorizationId + " not found"
            );
        }
        return authorization;
    }
}
