package net.ftgo.order.saga;

import io.eventuate.tram.commands.consumer.CommandWithDestination;
import net.ftgo.common.Money;
import net.ftgo.common.channels.ChannelNames;
import net.ftgo.common.orderflow.commands.RefundPaymentCommand;
import net.ftgo.common.orderflow.commands.VoidAuthorizationCommand;
import net.ftgo.order.domain.OrderPaymentState;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CancelOrderSagaPaymentRoutingTest {

    private final CancelOrderSaga saga = new CancelOrderSaga();

    @Test
    void authorizedPaymentRoutesToVoid() throws Exception {
        CommandWithDestination command = settlementCommand(data(OrderPaymentState.AUTHORIZED));

        assertThat(command.getDestinationChannel())
            .isEqualTo(ChannelNames.ACCOUNTING_SERVICE_COMMAND_CHANNEL);
        VoidAuthorizationCommand value = (VoidAuthorizationCommand) command.getCommand();
        assertThat(value.getAuthorizationId()).isEqualTo(501L);
        assertThat(value.getRequestId())
            .isEqualTo("cancel-order-101-authorization-501-void");
    }

    @Test
    void capturedPaymentRoutesToRefund() throws Exception {
        CommandWithDestination command = settlementCommand(data(OrderPaymentState.CAPTURED));

        assertThat(command.getDestinationChannel())
            .isEqualTo(ChannelNames.ACCOUNTING_SERVICE_COMMAND_CHANNEL);
        RefundPaymentCommand value = (RefundPaymentCommand) command.getCommand();
        assertThat(value.getAmount()).isEqualTo(new Money("25.00"));
        assertThat(value.getRequestId())
            .isEqualTo("cancel-order-101-capture-801-refund");
    }

    @Test
    void alreadySettledPaymentUsesIdempotentNoop() throws Exception {
        CommandWithDestination command = settlementCommand(data(OrderPaymentState.VOIDED));

        assertThat(command.getDestinationChannel())
            .isEqualTo(ChannelNames.ORDER_SERVICE_COMMAND_CHANNEL);
        assertThat(command.getCommand())
            .isInstanceOf(CancelOrderSagaLocalSteps.NoopFinancialSettlementCommand.class);
    }

    @Test
    void manualReviewCannotBeForcedToSuccess() throws Exception {
        InvocationTargetException error = assertThrows(
            InvocationTargetException.class,
            () -> settlementCommand(data(OrderPaymentState.MANUAL_REVIEW))
        );
        assertThat(error.getCause()).isInstanceOf(IllegalStateException.class);
    }

    private CancelOrderSagaData data(OrderPaymentState paymentState) {
        return new CancelOrderSagaData(
            101L,
            301L,
            901L,
            501L,
            801L,
            new Money("25.00"),
            paymentState
        );
    }

    private CommandWithDestination settlementCommand(CancelOrderSagaData data) throws Exception {
        Method method = CancelOrderSaga.class.getDeclaredMethod(
            "settlePayment",
            CancelOrderSagaData.class
        );
        method.setAccessible(true);
        return (CommandWithDestination) method.invoke(saga, data);
    }
}
