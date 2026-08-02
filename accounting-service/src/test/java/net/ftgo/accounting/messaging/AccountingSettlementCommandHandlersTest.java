package net.ftgo.accounting.messaging;

import io.eventuate.tram.commands.common.Command;
import io.eventuate.tram.commands.consumer.CommandMessage;
import io.eventuate.tram.messaging.common.Message;
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
import net.ftgo.accounting.settlement.SettlementGatewayTimeoutException;
import net.ftgo.accounting.settlement.SettlementRetryExhaustedException;
import net.ftgo.common.Money;
import net.ftgo.common.messaging.IdempotentCommandExecutor;
import net.ftgo.common.orderflow.commands.AuthorizeCardCommand;
import net.ftgo.common.orderflow.commands.CaptureAuthorizationCommand;
import net.ftgo.common.orderflow.commands.RefundPaymentCommand;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountingSettlementCommandHandlersTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private DomainEventPublisher eventPublisher;

    @Mock
    private PaymentAuthorizationGateway paymentAuthorizationGateway;

    @Mock
    private SettlementGateway settlementGateway;

    @Mock
    private PaymentLedgerService paymentLedgerService;

    private InMemoryProcessedCommandStore processedCommands;
    private AccountingServiceCommandHandlers handlers;

    @BeforeEach
    void setUp() {
        processedCommands = new InMemoryProcessedCommandStore();
        handlers = new AccountingServiceCommandHandlers(
            accountRepository,
            eventPublisher,
            paymentAuthorizationGateway,
            settlementGateway,
            paymentLedgerService,
            new IdempotentCommandExecutor(processedCommands)
        );
    }

    @Test
    void authorizePersistsProviderStateLedgerAndOutboxOnce() {
        when(paymentAuthorizationGateway.authorize("tok-101", new Money("42.50")))
            .thenReturn(PaymentAuthorizationDecision.allow());
        when(accountRepository.findByConsumerId(301L)).thenReturn(Optional.empty());
        when(accountRepository.saveAndFlush(any(Account.class))).thenAnswer(invocation -> {
            Account account = invocation.getArgument(0);
            if (account.getId() == null) {
                ReflectionTestUtils.setField(account, "id", 501L);
            }
            for (Authorization authorization : account.getAuthorizations()) {
                if (authorization.getId() == null) {
                    ReflectionTestUtils.setField(authorization, "id", 701L);
                }
            }
            return account;
        });
        when(settlementGateway.authorize(
            701L,
            101L,
            new Money("42.50"),
            "order-101-authorize"
        )).thenReturn(SettlementDecision.approved("provider-authorize-101"));

        CommandMessage<AuthorizeCardCommand> message = message(
            "command-authorize-101",
            new AuthorizeCardCommand(
                301L,
                101L,
                new Money("42.50"),
                "tok-101",
                "order-101-authorize"
            )
        );

        handlers.handleAuthorizeCard(message);
        handlers.handleAuthorizeCard(message);

        verify(settlementGateway, times(1)).authorize(
            701L,
            101L,
            new Money("42.50"),
            "order-101-authorize"
        );
        verify(paymentLedgerService, times(1)).append(
            501L,
            101L,
            701L,
            PaymentLedgerEntry.OperationType.AUTHORIZE,
            "order-101-authorize",
            new Money("42.50").getAmount(),
            "provider-authorize-101"
        );
        verify(eventPublisher, times(1)).publishAccountEvent(
            anyLong(),
            anyLong(),
            any(CardAuthorizedEvent.class)
        );
    }

    @Test
    void captureMutatesProviderAggregateLedgerAndOutboxOnce() {
        Account account = accountWithAuthorization(101L, 701L, new Money("42.50"));
        when(accountRepository.findByAuthorizationId(701L)).thenReturn(Optional.of(account));
        when(accountRepository.saveAndFlush(account)).thenReturn(account);
        when(settlementGateway.capture(701L, 101L, "order-101-capture"))
            .thenReturn(SettlementDecision.approved("provider-capture-101"));

        CommandMessage<CaptureAuthorizationCommand> message = message(
            "command-capture-101",
            new CaptureAuthorizationCommand(101L, 701L, "order-101-capture")
        );

        handlers.handleCaptureAuthorization(message);
        handlers.handleCaptureAuthorization(message);

        assertThat(account.findAuthorizationById(701L).getStatus())
            .isEqualTo(AuthorizationStatus.CAPTURED);
        verify(settlementGateway, times(1)).capture(701L, 101L, "order-101-capture");
        verify(paymentLedgerService, times(1)).append(
            501L,
            101L,
            701L,
            PaymentLedgerEntry.OperationType.CAPTURE,
            "order-101-capture",
            new Money("42.50").getAmount(),
            "provider-capture-101"
        );
        verify(eventPublisher, times(1)).publishAccountEvent(
            anyLong(),
            anyLong(),
            any(PaymentCapturedEvent.class)
        );
    }

    @Test
    void providerDenialLeavesLocalCaptureUntouched() {
        Account account = accountWithAuthorization(102L, 702L, new Money("25.00"));
        when(accountRepository.findByAuthorizationId(702L)).thenReturn(Optional.of(account));
        when(settlementGateway.capture(702L, 102L, "deny-capture-102"))
            .thenReturn(SettlementDecision.denied("SIMULATED_PROVIDER_DECLINED"));

        handlers.handleCaptureAuthorization(message(
            "command-capture-102",
            new CaptureAuthorizationCommand(102L, 702L, "deny-capture-102")
        ));

        assertThat(account.findAuthorizationById(702L).getStatus())
            .isEqualTo(AuthorizationStatus.AUTHORIZED);
        verify(accountRepository, never()).saveAndFlush(account);
        verify(paymentLedgerService, never()).append(
            anyLong(), anyLong(), anyLong(), any(), any(), any(), any()
        );
        verify(eventPublisher, never()).publishAccountEvent(
            anyLong(), anyLong(), any(PaymentCapturedEvent.class)
        );
    }

    @Test
    void gatewayTimeoutIsNotCachedAndCanBeRetried() {
        Account account = accountWithAuthorization(103L, 703L, new Money("30.00"));
        when(accountRepository.findByAuthorizationId(703L)).thenReturn(Optional.of(account));
        when(settlementGateway.capture(703L, 103L, "timeout-capture-103"))
            .thenThrow(new SettlementGatewayTimeoutException("timeout"))
            .thenReturn(SettlementDecision.approved("provider-capture-103"));
        when(accountRepository.saveAndFlush(account)).thenReturn(account);

        CommandMessage<CaptureAuthorizationCommand> message = message(
            "command-capture-103",
            new CaptureAuthorizationCommand(103L, 703L, "timeout-capture-103")
        );

        assertThatThrownBy(() -> handlers.handleCaptureAuthorization(message))
            .isInstanceOf(SettlementGatewayTimeoutException.class);
        assertThat(processedCommands.findCompleted(
            "accounting-service",
            "command-capture-103"
        )).isEmpty();

        handlers.handleCaptureAuthorization(message);

        assertThat(account.findAuthorizationById(703L).getStatus())
            .isEqualTo(AuthorizationStatus.CAPTURED);
        verify(settlementGateway, times(2)).capture(703L, 103L, "timeout-capture-103");
    }

    @Test
    void terminalRetryExhaustionIsCachedAsStableFailureReply() {
        Account account = accountWithAuthorization(105L, 705L, new Money("55.00"));
        when(accountRepository.findByAuthorizationId(705L)).thenReturn(Optional.of(account));
        when(settlementGateway.capture(705L, 105L, "timeout-always-capture-105"))
            .thenThrow(new SettlementRetryExhaustedException(
                "CAPTURE",
                4,
                new SettlementGatewayTimeoutException("timeout")
            ));

        CommandMessage<CaptureAuthorizationCommand> command = message(
            "command-capture-105",
            new CaptureAuthorizationCommand(105L, 705L, "timeout-always-capture-105")
        );

        Message first = handlers.handleCaptureAuthorization(command);
        Message duplicate = handlers.handleCaptureAuthorization(command);

        assertThat(first.getPayload()).isEqualTo(duplicate.getPayload());
        assertThat(first.getHeaders()).isEqualTo(duplicate.getHeaders());
        assertThat(first.getPayload()).contains(
            "Settlement CAPTURE exhausted after 4 attempts"
        );
        assertThat(processedCommands.findCompleted(
            "accounting-service",
            "command-capture-105"
        )).isPresent();
        verify(settlementGateway, times(1)).capture(
            705L,
            105L,
            "timeout-always-capture-105"
        );
        verify(accountRepository, never()).saveAndFlush(account);
        verify(paymentLedgerService, never()).append(
            anyLong(), anyLong(), anyLong(), any(), any(), any(), any()
        );
    }

    @Test
    void partialRefundWritesOnlyTheRefundAmount() {
        Account account = accountWithAuthorization(104L, 704L, new Money("100.00"));
        account.captureAuthorization(104L, 704L, "capture-104");
        when(accountRepository.findByAuthorizationId(704L)).thenReturn(Optional.of(account));
        when(accountRepository.saveAndFlush(account)).thenReturn(account);
        when(settlementGateway.refund(
            704L,
            104L,
            new Money("30.00"),
            "refund-104-1"
        )).thenReturn(SettlementDecision.approved("provider-refund-104-1"));

        handlers.handleRefundPayment(message(
            "command-refund-104-1",
            new RefundPaymentCommand(
                104L,
                704L,
                new Money("30.00"),
                "item unavailable",
                "refund-104-1"
            )
        ));

        assertThat(account.findAuthorizationById(704L).getStatus())
            .isEqualTo(AuthorizationStatus.PARTIALLY_REFUNDED);
        verify(paymentLedgerService).append(
            501L,
            104L,
            704L,
            PaymentLedgerEntry.OperationType.REFUND,
            "refund-104-1",
            new Money("30.00").getAmount(),
            "provider-refund-104-1"
        );
    }

    private Account accountWithAuthorization(Long orderId, Long authorizationId, Money amount) {
        Account account = new Account(301L);
        ReflectionTestUtils.setField(account, "id", 501L);
        ReflectionTestUtils.setField(account, "version", 0L);
        Authorization authorization = account.authorize(
            orderId,
            "order-" + orderId + "-authorize",
            amount
        );
        ReflectionTestUtils.setField(authorization, "id", authorizationId);
        return account;
    }

    private <T extends Command> CommandMessage<T> message(String id, T command) {
        return new CommandMessage<>(
            id,
            command,
            Map.of(),
            org.mockito.Mockito.mock(Message.class)
        );
    }
}
