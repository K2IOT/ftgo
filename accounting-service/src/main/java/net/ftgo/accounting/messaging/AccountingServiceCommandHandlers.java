package net.ftgo.accounting.messaging;

import io.eventuate.tram.commands.consumer.CommandHandlers;
import io.eventuate.tram.commands.consumer.CommandMessage;
import io.eventuate.tram.messaging.common.Message;
import io.eventuate.tram.sagas.participant.SagaCommandHandlersBuilder;
import net.ftgo.accounting.domain.Account;
import net.ftgo.accounting.domain.Authorization;
import net.ftgo.accounting.domain.AuthorizationStatus;
import net.ftgo.accounting.domain.FinancialOperationStatus;
import net.ftgo.accounting.domain.PaymentCapture;
import net.ftgo.accounting.domain.PaymentRefund;
import net.ftgo.accounting.payment.PaymentAuthorizationDecision;
import net.ftgo.accounting.payment.PaymentProvider;
import net.ftgo.accounting.payment.PaymentProviderResult;
import net.ftgo.accounting.repository.AccountRepository;
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

import java.time.LocalDateTime;

import static io.eventuate.tram.commands.consumer.CommandHandlerReplyBuilder.withFailure;
import static io.eventuate.tram.commands.consumer.CommandHandlerReplyBuilder.withSuccess;

@Component
public class AccountingServiceCommandHandlers {

    private static final String CONSUMER_NAME = "accounting-service";

    private final AccountRepository accountRepository;
    private final DomainEventPublisher eventPublisher;
    private final PaymentProvider paymentProvider;
    private final IdempotentCommandExecutor idempotentCommandExecutor;

    public AccountingServiceCommandHandlers(
        AccountRepository accountRepository,
        DomainEventPublisher eventPublisher,
        PaymentProvider paymentProvider,
        IdempotentCommandExecutor idempotentCommandExecutor
    ) {
        this.accountRepository = accountRepository;
        this.eventPublisher = eventPublisher;
        this.paymentProvider = paymentProvider;
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
            Account account = accountRepository.findByConsumerId(command.getConsumerId())
                .orElseGet(() -> new Account(command.getConsumerId()));
            Authorization existing = account.findAuthorizationByRequestId(command.getRequestId());
            if (existing != null) {
                if (command.getOrderId() != null
                    && !existing.matches(command.getOrderId(), command.getAmount())) {
                    return withFailure(new CardAuthorizationDenied(
                        command.getOrderId(),
                        "Request ID conflict"
                    ));
                }
                return withSuccess(new CardAuthorized(existing.getId(), command.getOrderId()));
            }

            PaymentAuthorizationDecision decision = paymentProvider.authorize(
                command.getPaymentToken(),
                command.getAmount(),
                command.getRequestId()
            );
            if (!decision.approved()) {
                return withFailure(new CardAuthorizationDenied(
                    command.getOrderId(),
                    decision.reason()
                ));
            }

            if (account.getId() == null) {
                accountRepository.saveAndFlush(account);
            }
            String providerAuthorizationId = decision.providerAuthorizationId() == null
                ? "pa_legacy_" + command.getRequestId()
                : decision.providerAuthorizationId();
            Authorization authorization = command.getOrderId() == null
                ? account.authorize(command.getRequestId(), command.getAmount())
                : account.authorize(
                    command.getOrderId(),
                    command.getRequestId(),
                    command.getAmount(),
                    providerAuthorizationId
                );
            accountRepository.saveAndFlush(account);

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
            Authorization authorization = account.requireAuthorizationById(command.getAuthorizationId());
            account.requireOrder(authorization, command.getOrderId());
            PaymentCapture capture = authorization.requestCapture(command.getRequestId());

            if (capture.getStatus() == FinancialOperationStatus.SUCCEEDED) {
                return withSuccess(capturedReply(capture, authorization, command.getOrderId()));
            }
            if (capture.getStatus() == FinancialOperationStatus.FAILED) {
                return withFailure(capture.getFailureCode());
            }

            accountRepository.saveAndFlush(account);
            PaymentProviderResult providerResult = paymentProvider.capture(
                authorization.getProviderAuthorizationId(),
                authorization.getAmount(),
                command.getRequestId()
            );
            if (!providerResult.successful()) {
                authorization.failCapture(command.getRequestId(), providerResult.failureCode());
                accountRepository.saveAndFlush(account);
                return withFailure(providerResult.failureCode());
            }

            authorization.completeCapture(
                command.getRequestId(),
                providerResult.providerReference()
            );
            accountRepository.saveAndFlush(account);
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
            return withSuccess(capturedReply(capture, authorization, command.getOrderId()));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return withFailure(e.getMessage());
        }
    }

    private Message voidAuthorizationOnce(VoidAuthorizationCommand command) {
        try {
            Account account = requireAccount(command.getAuthorizationId());
            Authorization authorization = account.requireAuthorizationById(command.getAuthorizationId());
            account.requireOrder(authorization, command.getOrderId());
            if (authorization.getStatus() == AuthorizationStatus.VOIDED
                && command.getRequestId().equals(authorization.getVoidRequestId())) {
                return withSuccess(new AuthorizationVoided(
                    command.getAuthorizationId(),
                    command.getOrderId()
                ));
            }

            PaymentProviderResult providerResult = paymentProvider.voidAuthorization(
                authorization.getProviderAuthorizationId(),
                command.getRequestId()
            );
            if (!providerResult.successful()) {
                return withFailure(providerResult.failureCode());
            }
            boolean changed = authorization.voidAuthorization(
                command.getReason(),
                command.getRequestId(),
                providerResult.providerReference()
            );
            accountRepository.saveAndFlush(account);
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
            Long authorizationId = command.getCaptureId();
            Account account = requireAccount(authorizationId);
            Authorization authorization = account.requireAuthorizationById(authorizationId);
            account.requireOrder(authorization, command.getOrderId());
            PaymentRefund refund = authorization.requestRefund(
                command.getAmount(),
                command.getReason(),
                command.getRequestId()
            );

            if (refund.getStatus() == FinancialOperationStatus.SUCCEEDED) {
                return withSuccess(refundedReply(refund, authorization, command.getOrderId()));
            }
            if (refund.getStatus() == FinancialOperationStatus.FAILED) {
                return withFailure(refund.getFailureCode());
            }

            accountRepository.saveAndFlush(account);
            PaymentProviderResult providerResult = paymentProvider.refund(
                authorization.getSuccessfulCapture().getProviderCaptureId(),
                command.getAmount(),
                command.getRequestId()
            );
            if (!providerResult.successful()) {
                authorization.failRefund(command.getRequestId(), providerResult.failureCode());
                accountRepository.saveAndFlush(account);
                return withFailure(providerResult.failureCode());
            }

            authorization.completeRefund(
                command.getRequestId(),
                providerResult.providerReference()
            );
            accountRepository.saveAndFlush(account);
            eventPublisher.publishAccountEvent(
                account.getId(),
                account.getVersion(),
                new PaymentRefundedEvent(
                    account.getId(),
                    command.getOrderId(),
                    authorizationId,
                    command.getReason(),
                    command.getRequestId(),
                    LocalDateTime.now()
                )
            );
            return withSuccess(refundedReply(refund, authorization, command.getOrderId()));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return withFailure(e.getMessage());
        }
    }

    private PaymentCaptured capturedReply(
        PaymentCapture capture,
        Authorization authorization,
        Long orderId
    ) {
        Long captureId = capture.getId() == null ? authorization.getId() : capture.getId();
        return new PaymentCaptured(captureId, authorization.getId(), orderId);
    }

    private PaymentRefunded refundedReply(
        PaymentRefund refund,
        Authorization authorization,
        Long orderId
    ) {
        Long refundId = refund.getId() == null ? authorization.getId() : refund.getId();
        Long captureId = authorization.getSuccessfulCapture().getId() == null
            ? authorization.getId()
            : authorization.getSuccessfulCapture().getId();
        return new PaymentRefunded(refundId, captureId, orderId);
    }

    private Message reverseAuthorizationOnce(ReverseAuthorizationCommand command) {
        try {
            Account account = accountRepository.findByConsumerId(command.getConsumerId())
                .orElseThrow(() -> new IllegalArgumentException(
                    "Account not found for consumer " + command.getConsumerId()
                ));
            account.reverseAuthorization(command.getAuthorizationId());
            accountRepository.saveAndFlush(account);
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

    private Account requireAccount(Long authorizationId) {
        return accountRepository.findByAuthorizationId(authorizationId)
            .orElseThrow(() -> new IllegalArgumentException(
                "Account not found for authorization " + authorizationId
            ));
    }
}
