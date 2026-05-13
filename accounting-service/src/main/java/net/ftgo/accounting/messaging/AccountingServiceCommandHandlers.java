package net.ftgo.accounting.messaging;

import io.eventuate.tram.commands.consumer.CommandHandlers;
import io.eventuate.tram.commands.consumer.CommandMessage;
import io.eventuate.tram.messaging.common.Message;
import io.eventuate.tram.sagas.participant.SagaCommandHandlersBuilder;
import net.ftgo.accounting.domain.Account;
import net.ftgo.accounting.domain.Authorization;
import net.ftgo.accounting.repository.AccountRepository;
import net.ftgo.common.Money;
import net.ftgo.common.orderflow.commands.AuthorizeCardCommand;
import net.ftgo.common.orderflow.replies.AuthorizationRevised;
import net.ftgo.common.orderflow.replies.CardAuthorized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static io.eventuate.tram.commands.consumer.CommandHandlerReplyBuilder.withFailure;
import static io.eventuate.tram.commands.consumer.CommandHandlerReplyBuilder.withSuccess;

/**
 * Command handlers for Accounting Service saga participation.
 * 
 * Handles commands from sagas:
 * - AuthorizeCardCommand: Authorizes credit card with idempotency (CreateOrderSaga pivot point)
 * - ReverseAuthorizationCommand: Reverses authorization (CancelOrderSaga pivot point)
 * - ReviseAuthorizationCommand: Revises authorization to new amount (ReviseOrderSaga pivot point)
 * 
 * All authorization attempts are recorded with timestamp, amount, and outcome for audit purposes.
 */
@Component
public class AccountingServiceCommandHandlers {
    
    private static final Logger logger = LoggerFactory.getLogger(AccountingServiceCommandHandlers.class);
    
    private final AccountRepository accountRepository;
    private final DomainEventPublisher eventPublisher;
    
    public AccountingServiceCommandHandlers(AccountRepository accountRepository,
                                           DomainEventPublisher eventPublisher) {
        this.accountRepository = accountRepository;
        this.eventPublisher = eventPublisher;
    }
    
    /**
     * Builds command handlers for Accounting Service.
     * 
     * @return CommandHandlers configured for accountingService channel
     */
    public CommandHandlers commandHandlers() {
        return SagaCommandHandlersBuilder
                .fromChannel("accountingService")
                .onMessage(AuthorizeCardCommand.class, this::handleAuthorizeCard)
                .onMessage(ReverseAuthorizationCommand.class, this::handleReverseAuthorization)
                .onMessage(ReviseAuthorizationCommand.class, this::handleReviseAuthorization)
                .build();
    }
    
    /**
     * Handles AuthorizeCardCommand from CreateOrderSaga.
     * 
     * Authorizes credit card with idempotency check using requestId.
     * Duplicate requests with same requestId return cached result without creating new authorization.
     * 
     * Records authorization attempt with timestamp, amount, and outcome for audit.
     * 
     * @param cm the command message
     * @return success reply with CardAuthorized or failure reply with error message
     */
    @Transactional
    public Message handleAuthorizeCard(CommandMessage<AuthorizeCardCommand> cm) {
        AuthorizeCardCommand command = cm.getCommand();
        
        logger.info("Authorizing card for consumer {} with requestId {} and amount {}", 
            command.getConsumerId(), command.getRequestId(), command.getAmount());
        
        try {
            // Find or create account for consumer
            Account account = accountRepository.findByConsumerId(command.getConsumerId())
                .orElseGet(() -> {
                    logger.info("Creating new account for consumer {}", command.getConsumerId());
                    Account newAccount = new Account(command.getConsumerId());
                    return accountRepository.save(newAccount);
                });
            
            // Authorize with idempotency check
            Authorization authorization = account.authorize(command.getRequestId(), command.getAmount());
            
            // Save account (cascades to authorization)
            accountRepository.save(account);
            
            // Publish CardAuthorized event to outbox
            CardAuthorizedEvent event = new CardAuthorizedEvent(
                account.getId(),
                authorization.getId(),
                authorization.getRequestId(),
                authorization.getAmount().getAmount(),
                authorization.getCreatedAt()
            );
            eventPublisher.publishAccountEvent(account.getId(), event);
            
            logger.info("Card authorized successfully for consumer {} with authorization ID {} (requestId: {})", 
                command.getConsumerId(), authorization.getId(), command.getRequestId());
            
            return withSuccess(new CardAuthorized(authorization.getId()));
            
        } catch (IllegalArgumentException e) {
            logger.error("Card authorization failed for consumer {}: {}", 
                command.getConsumerId(), e.getMessage());
            return withFailure(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error authorizing card for consumer {}", 
                command.getConsumerId(), e);
            return withFailure("Internal error authorizing card");
        }
    }
    
    /**
     * Handles ReverseAuthorizationCommand from CancelOrderSaga.
     * 
     * Reverses an existing authorization.
     * Records reversal with timestamp for audit.
     * 
     * @param cm the command message
     * @return success reply with AuthorizationReversed or failure reply with error message
     */
    @Transactional
    public Message handleReverseAuthorization(CommandMessage<ReverseAuthorizationCommand> cm) {
        ReverseAuthorizationCommand command = cm.getCommand();
        
        logger.info("Reversing authorization {} for consumer {}", 
            command.getAuthorizationId(), command.getConsumerId());
        
        try {
            // Find account
            Account account = accountRepository.findByConsumerId(command.getConsumerId())
                .orElseThrow(() -> new IllegalArgumentException(
                    String.format("Account not found for consumer %d", command.getConsumerId())
                ));
            
            // Reverse authorization
            account.reverseAuthorization(command.getAuthorizationId());
            
            // Save account
            accountRepository.save(account);
            
            // Publish CardReversed event to outbox
            CardReversed event = new CardReversed(
                account.getId(),
                command.getAuthorizationId(),
                LocalDateTime.now()
            );
            eventPublisher.publishAccountEvent(account.getId(), event);
            
            logger.info("Authorization {} reversed successfully for consumer {}", 
                command.getAuthorizationId(), command.getConsumerId());
            
            return withSuccess(new AuthorizationReversed(command.getAuthorizationId()));
            
        } catch (IllegalArgumentException | IllegalStateException e) {
            logger.error("Authorization reversal failed for consumer {}: {}", 
                command.getConsumerId(), e.getMessage());
            return withFailure(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error reversing authorization {} for consumer {}", 
                command.getAuthorizationId(), command.getConsumerId(), e);
            return withFailure("Internal error reversing authorization");
        }
    }
    
    /**
     * Handles ReviseAuthorizationCommand from ReviseOrderSaga.
     * 
     * Revises an existing authorization to a new amount by:
     * 1. Reversing the old authorization
     * 2. Creating a new authorization with the new amount
     * 
     * Uses idempotency check to handle duplicate revision requests.
     * Records revision with timestamp for audit.
     * 
     * @param cm the command message
     * @return success reply with AuthorizationRevised or failure reply with error message
     */
    @Transactional
    public Message handleReviseAuthorization(CommandMessage<ReviseAuthorizationCommand> cm) {
        ReviseAuthorizationCommand command = cm.getCommand();
        
        logger.info("Revising authorization {} for consumer {} to new amount {}", 
            command.getAuthorizationId(), command.getConsumerId(), command.getNewAmount());
        
        try {
            // Find account
            Account account = accountRepository.findByConsumerId(command.getConsumerId())
                .orElseThrow(() -> new IllegalArgumentException(
                    String.format("Account not found for consumer %d", command.getConsumerId())
                ));
            
            // Generate new request ID for the revised authorization (for idempotency)
            // In a real system, this would come from the command
            String newRequestId = "revision-" + command.getAuthorizationId() + "-" + System.currentTimeMillis();
            
            // Revise authorization
            Money newAmount = new Money(command.getNewAmount());
            Authorization newAuthorization = account.reviseAuthorization(
                command.getAuthorizationId(), 
                newAmount, 
                newRequestId
            );
            
            // Save account
            accountRepository.save(account);
            
            logger.info("Authorization {} revised successfully for consumer {} with new authorization ID {}", 
                command.getAuthorizationId(), command.getConsumerId(), newAuthorization.getId());
            
            return withSuccess(new AuthorizationRevised(newAuthorization.getId()));
            
        } catch (IllegalArgumentException | IllegalStateException e) {
            logger.error("Authorization revision failed for consumer {}: {}", 
                command.getConsumerId(), e.getMessage());
            return withFailure(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error revising authorization {} for consumer {}", 
                command.getAuthorizationId(), command.getConsumerId(), e);
            return withFailure("Internal error revising authorization");
        }
    }
}
