package net.ftgo.accounting.messaging;

import io.eventuate.tram.commands.consumer.CommandMessage;
import io.eventuate.tram.messaging.common.Message;
import net.ftgo.accounting.domain.Account;
import net.ftgo.accounting.domain.Authorization;
import net.ftgo.accounting.payment.PaymentProvider;
import net.ftgo.accounting.payment.PaymentProviderResult;
import net.ftgo.accounting.repository.AccountRepository;
import net.ftgo.common.Money;
import net.ftgo.common.messaging.IdempotentCommandExecutor;
import net.ftgo.common.orderflow.commands.CaptureAuthorizationCommand;
import net.ftgo.common.orderflow.commands.RefundPaymentCommand;
import net.ftgo.common.orderflow.commands.VoidAuthorizationCommand;
import net.ftgo.testsupport.IdempotentCommandContract;
import net.ftgo.testsupport.InMemoryProcessedCommandStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinancialCommandIdempotencyTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private DomainEventPublisher eventPublisher;

    @Mock
    private PaymentProvider paymentProvider;

    private AccountingServiceCommandHandlers handlers;

    @BeforeEach
    void setUp() {
        handlers = new AccountingServiceCommandHandlers(
            accountRepository,
            eventPublisher,
            paymentProvider,
            new IdempotentCommandExecutor(new InMemoryProcessedCommandStore())
        );
    }

    @Test
    void lostCaptureReplyReplaysResultWithoutDoubleCapture() {
        Account account = authorizedAccount();
        when(accountRepository.findByAuthorizationId(701L)).thenReturn(Optional.of(account));
        when(accountRepository.saveAndFlush(account)).thenReturn(account);
        when(paymentProvider.capture("pa_authorize_order_101", new Money("42.50"), "capture-order-101"))
            .thenReturn(PaymentProviderResult.succeeded("pc_capture_order_101"));
        CommandMessage<CaptureAuthorizationCommand> message = message(
            "eventuate-capture-command-101",
            new CaptureAuthorizationCommand(101L, 701L, "capture-order-101")
        );

        Message first = handlers.handleCaptureAuthorization(message);
        Message replayed = handlers.handleCaptureAuthorization(message);

        IdempotentCommandContract.assertByteEquivalentReply(first, replayed);
        verify(paymentProvider, times(1))
            .capture("pa_authorize_order_101", new Money("42.50"), "capture-order-101");
        verify(accountRepository, times(1)).findByAuthorizationId(701L);
        assertEquals(1, account.getAuthorizations().get(0).getPaymentCaptures().size());
    }

    @Test
    void lostVoidReplyReplaysResultWithoutDoubleVoid() {
        Account account = authorizedAccount();
        when(accountRepository.findByAuthorizationId(701L)).thenReturn(Optional.of(account));
        when(accountRepository.saveAndFlush(account)).thenReturn(account);
        when(paymentProvider.voidAuthorization("pa_authorize_order_101", "void-order-101"))
            .thenReturn(PaymentProviderResult.succeeded("pv_void_order_101"));
        CommandMessage<VoidAuthorizationCommand> message = message(
            "eventuate-void-command-101",
            new VoidAuthorizationCommand(101L, 701L, "capture declined", "void-order-101")
        );

        Message first = handlers.handleVoidAuthorization(message);
        Message replayed = handlers.handleVoidAuthorization(message);

        IdempotentCommandContract.assertByteEquivalentReply(first, replayed);
        verify(paymentProvider, times(1))
            .voidAuthorization("pa_authorize_order_101", "void-order-101");
        verify(accountRepository, times(1)).findByAuthorizationId(701L);
    }

    @Test
    void lostRefundReplyReplaysResultWithoutDoubleRefund() {
        Account account = capturedAccount();
        when(accountRepository.findByAuthorizationId(701L)).thenReturn(Optional.of(account));
        when(accountRepository.saveAndFlush(account)).thenReturn(account);
        when(paymentProvider.refund("pc_capture_order_101", new Money("42.50"), "refund-order-101"))
            .thenReturn(PaymentProviderResult.succeeded("pr_refund_order_101"));
        CommandMessage<RefundPaymentCommand> message = message(
            "eventuate-refund-command-101",
            new RefundPaymentCommand(
                101L,
                701L,
                new Money("42.50"),
                "order cancelled",
                "refund-order-101"
            )
        );

        Message first = handlers.handleRefundPayment(message);
        Message replayed = handlers.handleRefundPayment(message);

        IdempotentCommandContract.assertByteEquivalentReply(first, replayed);
        verify(paymentProvider, times(1))
            .refund("pc_capture_order_101", new Money("42.50"), "refund-order-101");
        verify(accountRepository, times(1)).findByAuthorizationId(701L);
        assertEquals(1, account.getAuthorizations().get(0).getPaymentRefunds().size());
    }

    private Account authorizedAccount() {
        Account account = new Account(202L);
        Authorization authorization = account.authorize(
            101L,
            "authorize-order-101",
            new Money("42.50"),
            "pa_authorize_order_101"
        );
        ReflectionTestUtils.setField(account, "id", 501L);
        ReflectionTestUtils.setField(account, "version", 0L);
        ReflectionTestUtils.setField(authorization, "id", 701L);
        return account;
    }

    private Account capturedAccount() {
        Account account = authorizedAccount();
        Authorization authorization = account.getAuthorizations().get(0);
        authorization.requestCapture("capture-order-101");
        authorization.completeCapture("capture-order-101", "pc_capture_order_101");
        ReflectionTestUtils.setField(
            authorization.getPaymentCaptures().get(0),
            "id",
            801L
        );
        return account;
    }

    private <T> CommandMessage<T> message(String messageId, T command) {
        return new CommandMessage<>(messageId, command, Map.of(), mock(Message.class));
    }
}
