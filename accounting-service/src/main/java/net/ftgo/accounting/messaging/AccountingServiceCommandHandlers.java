package net.ftgo.accounting.messaging;

import io.eventuate.tram.commands.consumer.CommandHandlers;
import io.eventuate.tram.commands.consumer.CommandMessage;
import io.eventuate.tram.messaging.common.Message;
import io.eventuate.tram.sagas.participant.SagaCommandHandlersBuilder;
import net.ftgo.accounting.domain.Account;
import net.ftgo.accounting.domain.Authorization;
import net.ftgo.accounting.payment.PaymentAuthorizationDecision;
import net.ftgo.accounting.payment.PaymentAuthorizationGateway;
import net.ftgo.accounting.repository.AccountRepository;
import net.ftgo.common.Money;
import net.ftgo.common.channels.ChannelNames;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static io.eventuate.tram.commands.consumer.CommandHandlerReplyBuilder.withFailure;
import static io.eventuate.tram.commands.consumer.CommandHandlerReplyBuilder.withSuccess;

@Component
public class AccountingServiceCommandHandlers {

    private final AccountRepository accountRepository;
    private final DomainEventPublisher eventPublisher;
    private final PaymentAuthorizationGateway paymentAuthorizationGateway;

    public AccountingServiceCommandHandlers(
        AccountRepository accountRepository,
        DomainEventPublisher eventPublisher
    ) {
        this(accountRepository, eventPublisher, (paymentToken, amount) ->
            PaymentAuthorizationDecision.allow());
    }

    @Autowired
    public AccountingServiceCommandHandlers(
        AccountRepository accountRepository,
        DomainEventPublisher eventPublisher,
        PaymentAuthorizationGateway paymentAuthorizationGateway
    ) {
        this.accountRepository = accountRepository;
        this.eventPublisher = eventPublisher;
        this.paymentAuthorizationGateway = paymentAuthorizationGateway;
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

    @Transactional
    public Message handleAuthorizeCard(CommandMessage<AuthorizeCardCommand> message) {
        AuthorizeCardCommand command = message.getCommand();
        try {
            PaymentAuthorizationDecision decision = paymentAuthorizationGateway.authorize(
                command.getPaymentToken(),
                command.getAmount()
            );
            if (!decision.approved()) {
                return withFailure(new CardAuthorizationDenied(
                    command.getOrderId(),
                    decision.reason()
                ));
            }

            Account account = accountRepository.findByConsumerId(command.getConsumerId())
                .orElseGet(() -> accountRepository.saveAndFlush(new Account(command.getConsumerId())));

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

            Authorization authorization = command.getOrderId() == null
                ? account.authorize(command.getRequestId(), command.getAmount())
                : account.authorize(
                    command.getOrderId(),
                    command.getRequestId(),
                    command.getAmount()
                );
            account = accountRepository.saveAndFlush(account);

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
        } catch (Exception e) {
            return withFailure("Internal error authorizing card");
        }
    }

    @Transactional
    public Message handleCaptureAuthorization(CommandMessage<CaptureAuthorizationCommand> message) {
        CaptureAuthorizationCommand command = message.getCommand();
        try {
            Account account = requireAccount(command.getAuthorizationId());
            boolean changed = account.captureAuthorization(
                command.getOrderId(),
                command.getAuthorizationId(),
                command.getRequestId()
            );
            account = accountRepository.saveAndFlush(account);
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

    @Transactional
    public Message handleVoidAuthorization(CommandMessage<VoidAuthorizationCommand> message) {
        VoidAuthorizationCommand command = message.getCommand();
        try {
            Account account = requireAccount(command.getAuthorizationId());
            boolean changed = account.voidAuthorization(
                command.getOrderId(),
                command.getAuthorizationId(),
                command.getReason(),
                command.getRequestId()
            );
            account = accountRepository.saveAndFlush(account);
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

    @Transactional
    public Message handleRefundPayment(CommandMessage<RefundPaymentCommand> message) {
        RefundPaymentCommand command = message.getCommand();
        try {
            Account account = requireAccount(command.getCaptureId());
            boolean changed = account.refundPayment(
                command.getOrderId(),
                command.getCaptureId(),
                command.getAmount(),
                command.getReason(),
                command.getRequestId()
            );
            account = accountRepository.saveAndFlush(account);
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

    @Transactional
    public Message handleReverseAuthorization(CommandMessage<ReverseAuthorizationCommand> message) {
        ReverseAuthorizationCommand command = message.getCommand();
        try {
            Account account = accountRepository.findByConsumerId(command.getConsumerId())
                .orElseThrow(() -> new IllegalArgumentException(
                    "Account not found for consumer " + command.getConsumerId()
                ));
            account.reverseAuthorization(command.getAuthorizationId());
            account = accountRepository.saveAndFlush(account);
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

    @Transactional
    public Message handleReviseAuthorization(CommandMessage<ReviseAuthorizationCommand> message) {
        ReviseAuthorizationCommand command = message.getCommand();
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
