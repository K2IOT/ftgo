package net.ftgo.accounting.messaging;

import io.eventuate.tram.commands.consumer.CommandMessage;
import io.eventuate.tram.messaging.common.Message;
import net.ftgo.accounting.domain.Account;
import net.ftgo.accounting.domain.Authorization;
import net.ftgo.accounting.domain.AuthorizationStatus;
import net.ftgo.accounting.payment.PaymentAuthorizationGateway;
import net.ftgo.accounting.repository.AccountRepository;
import net.ftgo.accounting.settlement.PaymentLedgerEntry;
import net.ftgo.accounting.settlement.PaymentLedgerService;
import net.ftgo.accounting.settlement.SettlementDecision;
import net.ftgo.accounting.settlement.SettlementGateway;
import net.ftgo.common.Money;
import net.ftgo.common.messaging.IdempotentCommandExecutor;
import net.ftgo.common.orderflow.commands.ReverseAuthorizationCommand;
import net.ftgo.testsupport.InMemoryProcessedCommandStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CancelPaymentSettlementTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private DomainEventPublisher eventPublisher;

    @Mock
    private PaymentAuthorizationGateway authorizationGateway;

    @Mock
    private SettlementGateway settlementGateway;

    @Mock
    private PaymentLedgerService ledgerService;

    private AccountingServiceCommandHandlers handlers;

    @BeforeEach
    void setUp() {
        handlers = new AccountingServiceCommandHandlers(
            accountRepository,
            eventPublisher,
            authorizationGateway,
            settlementGateway,
            ledgerService,
            new IdempotentCommandExecutor(new InMemoryProcessedCommandStore())
        );
    }

    @Test
    void cancelUncapturedAuthorizationVoidsProviderLocalLedgerAndOutboxOnce() {
        Account account = account(101L, 701L, "25.00", false);
        when(accountRepository.findByConsumerId(301L)).thenReturn(Optional.of(account));
        when(accountRepository.saveAndFlush(account)).thenReturn(account);
        when(settlementGateway.voidAuthorization(
            701L,
            101L,
            "order-101-cancel-settlement"
        )).thenReturn(SettlementDecision.approved("provider-void-701"));

        CommandMessage<ReverseAuthorizationCommand> message = message(
            "cancel-command-101",
            new ReverseAuthorizationCommand(
                301L,
                701L,
                101L,
                "order-101-cancel-settlement"
            )
        );

        handlers.handleReverseAuthorization(message);
        handlers.handleReverseAuthorization(message);

        assertThat(account.findAuthorizationById(701L).getStatus())
            .isEqualTo(AuthorizationStatus.VOIDED);
        verify(settlementGateway, times(1)).voidAuthorization(
            701L,
            101L,
            "order-101-cancel-settlement"
        );
        verify(ledgerService, times(1)).append(
            501L,
            101L,
            701L,
            PaymentLedgerEntry.OperationType.VOID,
            "order-101-cancel-settlement",
            new Money("25.00").getAmount(),
            "provider-void-701"
        );
        verify(eventPublisher, times(1)).publishAccountEvent(
            anyLong(), anyLong(), any(AuthorizationVoidedEvent.class)
        );
    }

    @Test
    void cancelCapturedAuthorizationRefundsRemainingAmount() {
        Account account = account(102L, 702L, "100.00", true);
        account.refundPayment(
            102L,
            702L,
            new Money("30.00"),
            "prior adjustment",
            "refund-702-prior"
        );
        when(accountRepository.findByConsumerId(301L)).thenReturn(Optional.of(account));
        when(accountRepository.saveAndFlush(account)).thenReturn(account);
        when(settlementGateway.refund(
            702L,
            102L,
            new Money("70.00"),
            "order-102-cancel-settlement"
        )).thenReturn(SettlementDecision.approved("provider-refund-702-cancel"));

        handlers.handleReverseAuthorization(message(
            "cancel-command-102",
            new ReverseAuthorizationCommand(
                301L,
                702L,
                102L,
                "order-102-cancel-settlement"
            )
        ));

        Authorization authorization = account.findAuthorizationById(702L);
        assertThat(authorization.getStatus()).isEqualTo(AuthorizationStatus.REFUNDED);
        assertThat(authorization.getRefundedAmount()).isEqualTo(new Money("100.00"));
        verify(ledgerService).append(
            501L,
            102L,
            702L,
            PaymentLedgerEntry.OperationType.REFUND,
            "order-102-cancel-settlement",
            new Money("70.00").getAmount(),
            "provider-refund-702-cancel"
        );
        verify(eventPublisher).publishAccountEvent(
            anyLong(), anyLong(), any(PaymentRefundedEvent.class)
        );
    }

    @Test
    void providerDenialDoesNotCancelLocalPayment() {
        Account account = account(103L, 703L, "30.00", true);
        when(accountRepository.findByConsumerId(301L)).thenReturn(Optional.of(account));
        when(settlementGateway.refund(
            703L,
            103L,
            new Money("30.00"),
            "order-103-cancel-settlement"
        )).thenReturn(SettlementDecision.denied("SIMULATED_PROVIDER_DECLINED"));

        handlers.handleReverseAuthorization(message(
            "cancel-command-103",
            new ReverseAuthorizationCommand(
                301L,
                703L,
                103L,
                "order-103-cancel-settlement"
            )
        ));

        assertThat(account.findAuthorizationById(703L).getStatus())
            .isEqualTo(AuthorizationStatus.CAPTURED);
        verify(accountRepository, never()).saveAndFlush(account);
        verify(ledgerService, never()).append(
            anyLong(), anyLong(), anyLong(), any(), any(), any(), any()
        );
    }

    private Account account(
        Long orderId,
        Long authorizationId,
        String amount,
        boolean captured
    ) {
        Account account = new Account(301L);
        ReflectionTestUtils.setField(account, "id", 501L);
        ReflectionTestUtils.setField(account, "version", 0L);
        Authorization authorization = account.authorize(
            orderId,
            "authorize-" + authorizationId,
            new Money(amount)
        );
        ReflectionTestUtils.setField(authorization, "id", authorizationId);
        if (captured) authorization.capture("capture-" + authorizationId);
        return account;
    }

    private CommandMessage<ReverseAuthorizationCommand> message(
        String messageId,
        ReverseAuthorizationCommand command
    ) {
        return new CommandMessage<>(
            messageId,
            command,
            Map.of(),
            org.mockito.Mockito.mock(Message.class)
        );
    }
}