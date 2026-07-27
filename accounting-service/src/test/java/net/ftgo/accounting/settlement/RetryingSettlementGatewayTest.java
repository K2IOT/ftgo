package net.ftgo.accounting.settlement;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.ftgo.common.Money;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RetryingSettlementGatewayTest {

    @Test
    void retriesTimeoutOnceAndReturnsTheSuccessfulDecision() {
        SettlementGateway delegate = mock(SettlementGateway.class);
        when(delegate.capture(701L, 101L, "order-101-capture"))
            .thenThrow(new SettlementGatewayTimeoutException("timeout"))
            .thenReturn(SettlementDecision.approved("provider-capture-701"));
        List<Duration> slept = new ArrayList<>();
        RetryingSettlementGateway gateway = new RetryingSettlementGateway(
            delegate,
            new SimpleMeterRegistry(),
            List.of(Duration.ofSeconds(1), Duration.ofSeconds(5), Duration.ofSeconds(30)),
            slept::add
        );

        SettlementDecision decision = gateway.capture(
            701L,
            101L,
            "order-101-capture"
        );

        assertThat(decision.approved()).isTrue();
        assertThat(decision.providerReference()).isEqualTo("provider-capture-701");
        assertThat(slept).containsExactly(Duration.ofSeconds(1));
        verify(delegate, times(2)).capture(701L, 101L, "order-101-capture");
    }

    @Test
    void exhaustsAfterOneInitialAttemptAndThreeConfiguredRetries() {
        SettlementGateway delegate = mock(SettlementGateway.class);
        when(delegate.refund(
            702L,
            102L,
            new Money("10.00"),
            "refund-702-timeout"
        )).thenThrow(new SettlementGatewayTimeoutException("timeout"));
        AtomicInteger sleeps = new AtomicInteger();
        RetryingSettlementGateway gateway = new RetryingSettlementGateway(
            delegate,
            new SimpleMeterRegistry(),
            List.of(Duration.ZERO, Duration.ZERO, Duration.ZERO),
            ignored -> sleeps.incrementAndGet()
        );

        assertThatThrownBy(() -> gateway.refund(
            702L,
            102L,
            new Money("10.00"),
            "refund-702-timeout"
        )).isInstanceOf(SettlementRetryExhaustedException.class)
          .hasMessageContaining("REFUND")
          .hasMessageContaining("4 attempts");

        assertThat(sleeps).hasValue(3);
        verify(delegate, times(4)).refund(
            702L,
            102L,
            new Money("10.00"),
            "refund-702-timeout"
        );
    }

    @Test
    void denialIsReturnedWithoutRetry() {
        SettlementGateway delegate = mock(SettlementGateway.class);
        when(delegate.voidAuthorization(703L, 103L, "void-703"))
            .thenReturn(SettlementDecision.denied("declined"));
        AtomicInteger sleeps = new AtomicInteger();
        RetryingSettlementGateway gateway = new RetryingSettlementGateway(
            delegate,
            new SimpleMeterRegistry(),
            List.of(Duration.ofSeconds(1)),
            ignored -> sleeps.incrementAndGet()
        );

        SettlementDecision decision = gateway.voidAuthorization(703L, 103L, "void-703");

        assertThat(decision.approved()).isFalse();
        assertThat(sleeps).hasValue(0);
        verify(delegate, times(1)).voidAuthorization(703L, 103L, "void-703");
    }

    @Test
    void readOnlyProviderLookupIsNotRetriedOrDelayed() {
        SettlementGateway delegate = mock(SettlementGateway.class);
        ProviderSettlementSnapshot snapshot = new ProviderSettlementSnapshot(
            704L,
            104L,
            new Money("25.00"),
            new Money("25.00"),
            Money.ZERO,
            ProviderSettlementStatus.CAPTURED,
            "provider-704"
        );
        when(delegate.find(704L)).thenReturn(Optional.of(snapshot));
        AtomicInteger sleeps = new AtomicInteger();
        RetryingSettlementGateway gateway = new RetryingSettlementGateway(
            delegate,
            new SimpleMeterRegistry(),
            List.of(Duration.ofSeconds(1)),
            ignored -> sleeps.incrementAndGet()
        );

        assertThat(gateway.find(704L)).contains(snapshot);
        assertThat(sleeps).hasValue(0);
        verify(delegate, times(1)).find(704L);
    }
}