package net.ftgo.accounting.settlement;

import net.ftgo.common.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SimulatedSettlementGatewayTest {

    @Mock
    private SimulatedProviderPaymentRepository paymentRepository;

    @Mock
    private SimulatedProviderOperationRepository operationRepository;

    private final Map<Long, SimulatedProviderPayment> payments = new HashMap<>();
    private final Map<String, SimulatedProviderOperation> operations = new HashMap<>();

    private SimulatedSettlementGateway gateway;

    @BeforeEach
    void setUp() {
        when(paymentRepository.findByAuthorizationId(anyLong()))
            .thenAnswer(invocation -> Optional.ofNullable(payments.get(invocation.getArgument(0))));
        when(paymentRepository.saveAndFlush(any(SimulatedProviderPayment.class)))
            .thenAnswer(invocation -> {
                SimulatedProviderPayment payment = invocation.getArgument(0);
                payments.put(payment.getAuthorizationId(), payment);
                return payment;
            });
        when(operationRepository.findByRequestId(anyString()))
            .thenAnswer(invocation -> Optional.ofNullable(operations.get(invocation.getArgument(0))));
        when(operationRepository.saveAndFlush(any(SimulatedProviderOperation.class)))
            .thenAnswer(invocation -> {
                SimulatedProviderOperation operation = invocation.getArgument(0);
                operations.put(operation.getRequestId(), operation);
                return operation;
            });

        gateway = new SimulatedSettlementGateway(
            paymentRepository,
            operationRepository,
            "deny-capture",
            "timeout-once-capture",
            "timeout-always-refund"
        );
    }

    @Test
    void authorizesAndCapturesOnceWithStableProviderReference() {
        gateway.authorize(201L, 101L, new Money("42.50"), "authorize-101");

        SettlementDecision first = gateway.capture(201L, 101L, "capture-101");
        SettlementDecision replay = gateway.capture(201L, 101L, "capture-101");

        assertThat(first.approved()).isTrue();
        assertThat(replay.providerReference()).isEqualTo(first.providerReference());
        ProviderSettlementSnapshot snapshot = gateway.find(201L).orElseThrow();
        assertThat(snapshot.status()).isEqualTo(ProviderSettlementStatus.CAPTURED);
        assertThat(snapshot.capturedAmount()).isEqualTo(new Money("42.50"));
        assertThat(operations).hasSize(2);
    }

    @Test
    void rejectsDifferentPayloadForSameRequestIdAsConflict() {
        gateway.authorize(206L, 106L, new Money("50.00"), "authorize-106");
        gateway.capture(206L, 106L, "capture-106");
        gateway.refund(206L, 106L, new Money("10.00"), "refund-106");

        assertThatThrownBy(() -> gateway.refund(
            206L,
            106L,
            new Money("5.00"),
            "refund-106"
        )).isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("request ID conflict");

        assertThat(gateway.find(206L).orElseThrow().refundedAmount())
            .isEqualTo(new Money("10.00"));
    }

    @Test
    void configuredDenialDoesNotCaptureProviderPayment() {
        gateway.authorize(202L, 102L, new Money("25.00"), "authorize-102");

        SettlementDecision decision = gateway.capture(202L, 102L, "deny-capture");

        assertThat(decision.approved()).isFalse();
        assertThat(decision.reason()).isEqualTo("SIMULATED_PROVIDER_DECLINED");
        assertThat(gateway.find(202L).orElseThrow().status())
            .isEqualTo(ProviderSettlementStatus.AUTHORIZED);
    }

    @Test
    void timeoutOnceFailsFirstAttemptAndSucceedsOnRetry() {
        gateway.authorize(203L, 103L, new Money("30.00"), "authorize-103");

        assertThatThrownBy(() -> gateway.capture(203L, 103L, "timeout-once-capture"))
            .isInstanceOf(SettlementGatewayTimeoutException.class);

        SettlementDecision retry = gateway.capture(203L, 103L, "timeout-once-capture");

        assertThat(retry.approved()).isTrue();
        assertThat(operations.get("timeout-once-capture").getAttemptCount()).isEqualTo(2);
        assertThat(gateway.find(203L).orElseThrow().status())
            .isEqualTo(ProviderSettlementStatus.CAPTURED);
    }

    @Test
    void supportsMultiplePartialRefundsAndRejectsOverRefund() {
        gateway.authorize(204L, 104L, new Money("100.00"), "authorize-104");
        gateway.capture(204L, 104L, "capture-104");

        gateway.refund(204L, 104L, new Money("30.00"), "refund-104-1");
        ProviderSettlementSnapshot partial = gateway.find(204L).orElseThrow();
        assertThat(partial.status()).isEqualTo(ProviderSettlementStatus.PARTIALLY_REFUNDED);
        assertThat(partial.refundedAmount()).isEqualTo(new Money("30.00"));

        gateway.refund(204L, 104L, new Money("70.00"), "refund-104-2");
        ProviderSettlementSnapshot complete = gateway.find(204L).orElseThrow();
        assertThat(complete.status()).isEqualTo(ProviderSettlementStatus.REFUNDED);
        assertThat(complete.refundedAmount()).isEqualTo(new Money("100.00"));

        assertThatThrownBy(() -> gateway.refund(
            204L,
            104L,
            new Money("1.00"),
            "refund-104-3"
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("exceeds captured amount");
    }

    @Test
    void permanentTimeoutRemainsRetryableWithoutChangingProviderState() {
        gateway.authorize(205L, 105L, new Money("50.00"), "authorize-105");
        gateway.capture(205L, 105L, "capture-105");

        assertThatThrownBy(() -> gateway.refund(
            205L,
            105L,
            new Money("10.00"),
            "timeout-always-refund"
        )).isInstanceOf(SettlementGatewayTimeoutException.class);
        assertThatThrownBy(() -> gateway.refund(
            205L,
            105L,
            new Money("10.00"),
            "timeout-always-refund"
        )).isInstanceOf(SettlementGatewayTimeoutException.class);

        assertThat(gateway.find(205L).orElseThrow().refundedAmount()).isEqualTo(Money.ZERO);
        assertThat(operations.get("timeout-always-refund").getAttemptCount()).isEqualTo(2);
    }
}
