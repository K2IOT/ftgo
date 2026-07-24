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
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
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
        AccountingServiceCommandHandlers handlers = new AccountingServiceCommandHandlers(
            accountRepository,
            eventPublisher,
            paymentAuthorizationGateway,
            executor
        );
        AuthorizeCardCommand command = new AuthorizeCardCommand(
            202L,
            101L,
            new Money("42.50"),
            "tok_phase03",
            "authorization-request-101"
        );
        CommandMessage<AuthorizeCardCommand> message = new CommandMessage<>(
            "eventuate-command-authorization-101",
            command,
            Map.of(),
            mock(Message.class)
        );

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

        Message first = handlers.handleAuthorizeCard(message);
        Message replayed = handlers.handleAuthorizeCard(message);

        IdempotentCommandContract.assertByteEquivalentReply(first, replayed);
        verify(paymentAuthorizationGateway, times(1))
            .authorize("tok_phase03", new Money("42.50"));
        verify(accountRepository, times(1)).findByConsumerId(202L);
        verify(accountRepository, times(2)).saveAndFlush(any(Account.class));
    }
}
