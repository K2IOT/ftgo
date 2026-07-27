package net.ftgo.accounting.settlement;

import net.ftgo.accounting.domain.Account;
import net.ftgo.accounting.domain.Authorization;
import net.ftgo.accounting.domain.AuthorizationStatus;
import net.ftgo.accounting.messaging.DomainEventPublisher;
import net.ftgo.accounting.messaging.PaymentRefundedEvent;
import net.ftgo.accounting.repository.AccountRepository;
import net.ftgo.common.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManualPaymentSettlementServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private SettlementGateway settlementGateway;

    @Mock
    private PaymentLedgerService ledgerService;

    @Mock
    private DomainEventPublisher eventPublisher;

    private ManualPaymentSettlementService service;

    @BeforeEach
    void setUp() {
        service = new ManualPaymentSettlementService(
            accountRepository,
            settlementGateway,
            ledgerService,
            eventPublisher
        );
    }

    @Test
    void partialRefundUpdatesProviderLocalLedgerAndOutboxOnce() {
        Account account = capturedAccount(101L, 701L, "100.00");
        when(accountRepository.findByAuthorizationId(701L)).thenReturn(Optional.of(account));
        when(accountRepository.saveAndFlush(account)).thenReturn(account);
        when(settlementGateway.refund(
            701L,
            101L,
            new Money("30.00"),
            "manual-refund-701-1"
        )).thenReturn(SettlementDecision.approved("provider-refund-701-1"));

        ManualPaymentSettlementService.RefundResult first = service.refund(
            701L,
            new Money("30.00"),
            "item unavailable",
            "manual-refund-701-1"
        );
        ManualPaymentSettlementService.RefundResult replay = service.refund(
            701L,
            new Money("30.00"),
            "item unavailable",
            "manual-refund-701-1"
        );

        assertThat(first.status()).isEqualTo(AuthorizationStatus.PARTIALLY_REFUNDED);
        assertThat(first.refundedAmount()).isEqualTo(new Money("30.00"));
        assertThat(first.refundableAmount()).isEqualTo(new Money("70.00"));
        assertThat(replay).isEqualTo(first);
        verify(accountRepository, times(1)).saveAndFlush(account);
        verify(ledgerService, times(2)).append(
            501L,
            101L,
            701L,
            PaymentLedgerEntry.OperationType.REFUND,
            "manual-refund-701-1",
            new Money("30.00").getAmount(),
            "provider-refund-701-1"
        );
        verify(eventPublisher, times(1)).publishAccountEvent(
            anyLong(),
            anyLong(),
            any(PaymentRefundedEvent.class)
        );
    }

    @Test
    void overRefundIsRejectedBeforeLocalMutation() {
        Account account = capturedAccount(102L, 702L, "25.00");
        when(accountRepository.findByAuthorizationId(702L)).thenReturn(Optional.of(account));
        when(settlementGateway.refund(
            702L,
            102L,
            new Money("30.00"),
            "manual-refund-702-1"
        )).thenThrow(new IllegalArgumentException("Refund total exceeds captured amount"));

        assertThatThrownBy(() -> service.refund(
            702L,
            new Money("30.00"),
            "too much",
            "manual-refund-702-1"
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("exceeds captured amount");

        assertThat(account.findAuthorizationById(702L).getStatus())
            .isEqualTo(AuthorizationStatus.CAPTURED);
    }

    @Test
    void providerTimeoutRemainsRetryable() {
        Account account = capturedAccount(103L, 703L, "50.00");
        when(accountRepository.findByAuthorizationId(703L)).thenReturn(Optional.of(account));
        when(settlementGateway.refund(
            703L,
            103L,
            new Money("10.00"),
            "manual-refund-timeout"
        )).thenThrow(new SettlementGatewayTimeoutException("timeout"));

        assertThatThrownBy(() -> service.refund(
            703L,
            new Money("10.00"),
            "retry later",
            "manual-refund-timeout"
        )).isInstanceOf(SettlementGatewayTimeoutException.class);
        assertThat(account.findAuthorizationById(703L).getRefundedAmount())
            .isEqualTo(Money.ZERO);
    }

    private Account capturedAccount(Long orderId, Long authorizationId, String amount) {
        Account account = new Account(301L);
        ReflectionTestUtils.setField(account, "id", 501L);
        ReflectionTestUtils.setField(account, "version", 0L);
        Authorization authorization = account.authorize(
            orderId,
            "authorize-" + authorizationId,
            new Money(amount)
        );
        ReflectionTestUtils.setField(authorization, "id", authorizationId);
        authorization.capture("capture-" + authorizationId);
        return account;
    }
}