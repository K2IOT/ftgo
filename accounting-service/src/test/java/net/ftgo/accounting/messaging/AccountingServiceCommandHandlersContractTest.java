package net.ftgo.accounting.messaging;

import io.eventuate.tram.commands.consumer.CommandMessage;
import net.ftgo.accounting.domain.Account;
import net.ftgo.accounting.domain.Authorization;
import net.ftgo.accounting.domain.AuthorizationStatus;
import net.ftgo.accounting.repository.AccountRepository;
import net.ftgo.common.Money;
import net.ftgo.common.orderflow.commands.ReviseAuthorizationCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

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
        handlers = new AccountingServiceCommandHandlers(accountRepository, eventPublisher);
    }

    @Test
    void repeatedRevisionRetriesWithSameRequestIdReturnSameAuthorizationResult() throws Exception {
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

        handlers.handleReviseAuthorization(cm);
        handlers.handleReviseAuthorization(cm);

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
}
