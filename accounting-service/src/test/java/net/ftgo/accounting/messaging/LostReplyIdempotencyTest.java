package net.ftgo.accounting.messaging;

import io.eventuate.tram.commands.consumer.CommandMessage;
import io.eventuate.tram.messaging.common.Message;
import net.ftgo.accounting.domain.Account;
import net.ftgo.accounting.domain.Authorization;
import net.ftgo.accounting.payment.PaymentAuthorizationDecision;
import net.ftgo.accounting.payment.PaymentAuthorizationGateway;
import net.ftgo.accounting.repository.AccountRepository;
import net.ftgo.common.Money;
import net.ftgo.common.messaging.IdempotentCommandExecutor;
import net.ftgo.common.orderflow.commands.AuthorizeCardCommand;
import net.ftgo.testsupport.IdempotentCommandContract;
import net.ftgo.testsupport.InMemoryProcessedCommandStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LostReplyIdempotencyTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private DomainEventPublisher eventPublisher;

    @Mock
    private PaymentAuthorizationGateway paymentAuthorizationGateway;

    @Test
    void duplicateAuthorizeCommandReplaysEstablishedAuthorizationReply() {
        IdempotentCommandExecutor executor = new IdempotentCommandExecutor(
            new InMemoryProcessedCommandStore()
        );
        AccountingServiceCommandHandlers handlers = handlers(executor);
        AuthorizeCardCommand command = command();
        CommandMessage<AuthorizeCardCommand> message = message(
            "eventuate-command-authorization-101",
            command
        );
        stubSuccessfulAuthorization();

        Message first = handlers.handleAuthorizeCard(message);
        Message replayed = handlers.handleAuthorizeCard(message);

        IdempotentCommandContract.assertByteEquivalentReply(first, replayed);
        verify(paymentAuthorizationGateway, times(1))
            .authorize("tok_phase03", new Money("42.50"));
        verify(accountRepository, times(1)).findByConsumerId(202L);
        verify(accountRepository, times(2)).saveAndFlush(any(Account.class));
    }

    @Test
    void unexpectedOutboxFailureIsNotCachedAsSagaReply() {
        InMemoryProcessedCommandStore store = new InMemoryProcessedCommandStore();
        AccountingServiceCommandHandlers handlers = handlers(
            new IdempotentCommandExecutor(store)
        );
        AuthorizeCardCommand command = command();
        CommandMessage<AuthorizeCardCommand> message = message(
            "eventuate-command-authorization-outbox-failure",
            command
        );
        stubSuccessfulAuthorization();
        doThrow(new DataAccessResourceFailureException("accounting outbox unavailable"))
            .when(eventPublisher)
            .publishAccountEvent(anyLong(), anyLong(), any(CardAuthorizedEvent.class));

        assertThrows(
            DataAccessResourceFailureException.class,
            () -> handlers.handleAuthorizeCard(message)
        );
        assertTrue(store.findCompleted(
            "accounting-service",
            "eventuate-command-authorization-outbox-failure"
        ).isEmpty());
    }

    private AccountingServiceCommandHandlers handlers(IdempotentCommandExecutor executor) {
        return new AccountingServiceCommandHandlers(
            accountRepository,
            eventPublisher,
            paymentAuthorizationGateway,
            executor
        );
    }

    private AuthorizeCardCommand command() {
        return new AuthorizeCardCommand(
            202L,
            101L,
            new Money("42.50"),
            "tok_phase03",
            "authorization-request-101"
        );
    }

    private CommandMessage<AuthorizeCardCommand> message(
        String messageId,
        AuthorizeCardCommand command
    ) {
        return new CommandMessage<>(
            messageId,
            command,
            Map.of(),
            mock(Message.class)
        );
    }

    private void stubSuccessfulAuthorization() {
        when(paymentAuthorizationGateway.authorize("tok_phase03", new Money("42.50")))
            .thenReturn(PaymentAuthorizationDecision.allow());
        when(accountRepository.findByConsumerId(202L)).thenReturn(Optional.empty());
        when(accountRepository.saveAndFlush(any(Account.class))).thenAnswer(invocation -> {
            Account account = invocation.getArgument(0);
            if (account.getId() == null) {
                ReflectionTestUtils.setField(account, "id", 501L);
                ReflectionTestUtils.setField(account, "version", 0L);
            }
            for (Authorization authorization : account.getAuthorizations()) {
                if (authorization.getId() == null) {
                    ReflectionTestUtils.setField(authorization, "id", 701L);
                }
            }
            return account;
        });
    }
}
