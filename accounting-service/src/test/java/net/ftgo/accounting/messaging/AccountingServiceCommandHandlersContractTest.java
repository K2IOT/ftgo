package net.ftgo.accounting.messaging;

import io.eventuate.tram.commands.consumer.CommandMessage;
import net.ftgo.accounting.domain.Account;
import net.ftgo.accounting.domain.Authorization;
import net.ftgo.accounting.domain.AuthorizationStatus;
import net.ftgo.accounting.payment.PaymentAuthorizationDecision;
import net.ftgo.accounting.repository.AccountRepository;
import net.ftgo.accounting.settlement.PaymentLedgerService;
import net.ftgo.accounting.settlement.SettlementGateway;
import net.ftgo.common.Money;
import net.ftgo.common.messaging.IdempotentCommandExecutor;
import net.ftgo.common.messaging.ProcessedCommandResult;
import net.ftgo.common.messaging.ProcessedCommandStore;
import net.ftgo.common.orderflow.commands.ReviseAuthorizationCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccountingServiceCommandHandlersContractTest {

    private AccountRepository accountRepository;
    private DomainEventPublisher eventPublisher;
    private AccountingServiceCommandHandlers handlers;

    @BeforeEach
    void setUp() {
        accountRepository = mock(AccountRepository.class);
        eventPublisher = mock(DomainEventPublisher.class);
        handlers = new AccountingServiceCommandHandlers(
            accountRepository,
            eventPublisher,
            (paymentToken, amount) -> PaymentAuthorizationDecision.allow(),
            mock(SettlementGateway.class),
            mock(PaymentLedgerService.class),
            new IdempotentCommandExecutor(new InMemoryProcessedCommandStore())
        );
    }

    @Test
    void repeatedRevisionRetriesWithSameCommandIdReturnSameAuthorizationResult() throws Exception {
        Account account = new Account(100L);
        Authorization original = account.authorize("auth-original", new Money(new BigDecimal("100.00")));
        setAuthorizationId(original, 300L);

        when(accountRepository.findByConsumerId(100L)).thenReturn(Optional.of(account));
        when(accountRepository.save(account)).thenReturn(account);

        ReviseAuthorizationCommand command = new ReviseAuthorizationCommand(
            100L,
            300L,
            new BigDecimal("120.00"),
            "revision-request-xyz"
        );
        CommandMessage<ReviseAuthorizationCommand> cm = mock(CommandMessage.class);
        when(cm.getCommand()).thenReturn(command);
        when(cm.getMessageId()).thenReturn("accounting-command-1");

        var firstReply = handlers.handleReviseAuthorization(cm);
        var replayedReply = handlers.handleReviseAuthorization(cm);

        assertEquals(firstReply.getPayload(), replayedReply.getPayload());
        assertEquals(firstReply.getHeaders(), replayedReply.getHeaders());
        assertEquals(2, account.getAuthorizations().size());
        assertEquals(1L, account.getAuthorizations().stream()
            .filter(auth -> "revision-request-xyz".equals(auth.getRequestId()))
            .count());
        assertEquals(AuthorizationStatus.REVERSED, original.getStatus());
    }

    private void setAuthorizationId(Authorization authorization, Long id) {
        try {
            var idField = Authorization.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(authorization, id);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set authorization ID", e);
        }
    }

    private static final class InMemoryProcessedCommandStore implements ProcessedCommandStore {

        private final Set<String> started = new HashSet<>();
        private final Map<String, ProcessedCommandResult> completed = new HashMap<>();

        @Override
        public boolean tryStart(String consumerName, String commandId) {
            return started.add(key(consumerName, commandId));
        }

        @Override
        public void complete(
            String consumerName,
            String commandId,
            ProcessedCommandResult result
        ) {
            completed.put(key(consumerName, commandId), result);
        }

        @Override
        public Optional<ProcessedCommandResult> findCompleted(
            String consumerName,
            String commandId
        ) {
            return Optional.ofNullable(completed.get(key(consumerName, commandId)));
        }

        private String key(String consumerName, String commandId) {
            return consumerName + ":" + commandId;
        }
    }
}