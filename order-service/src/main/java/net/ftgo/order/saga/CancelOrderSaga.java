package net.ftgo.order.saga;

import io.eventuate.tram.commands.consumer.CommandWithDestination;
import io.eventuate.tram.sagas.orchestration.SagaDefinition;
import io.eventuate.tram.sagas.simpledsl.SimpleSaga;
import net.ftgo.common.channels.ChannelNames;
import net.ftgo.common.orderflow.commands.BeginCancelTicketCommand;
import net.ftgo.common.orderflow.commands.ConfirmCancelTicketCommand;
import net.ftgo.common.orderflow.commands.RefundPaymentCommand;
import net.ftgo.common.orderflow.commands.UndoCancelTicketCommand;
import net.ftgo.common.orderflow.commands.VoidAuthorizationCommand;
import net.ftgo.order.domain.OrderPaymentState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.eventuate.tram.commands.consumer.CommandWithDestinationBuilder.send;

/** Cancels the kitchen ticket and settles payment from durable financial state. */
public class CancelOrderSaga implements SimpleSaga<CancelOrderSagaData> {

    private static final Logger logger = LoggerFactory.getLogger(CancelOrderSaga.class);

    private final CancelOrderSagaLocalSteps localSteps;
    private final SagaDefinition<CancelOrderSagaData> sagaDefinition;

    public CancelOrderSaga() {
        this(null);
    }

    public CancelOrderSaga(CancelOrderSagaLocalSteps localSteps) {
        this.localSteps = localSteps;
        this.sagaDefinition = step()
            .invokeLocal(this::beginCancel)
            .withCompensation(this::undoCancel)
        .step()
            .invokeParticipant(this::beginCancelTicket)
            .withCompensation(this::undoCancelTicket)
        .step()
            .invokeParticipant(this::settlePayment)
        .step()
            .invokeLocal(this::recordFinancialSettlement)
        .step()
            .invokeParticipant(this::confirmCancelTicket)
        .step()
            .invokeParticipant(this::confirmCancelStep)
        .build();
    }

    @Override
    public SagaDefinition<CancelOrderSagaData> getSagaDefinition() {
        return sagaDefinition;
    }

    private void beginCancel(CancelOrderSagaData data) {
        logger.info("CancelOrderSaga: begin cancellation for orderId={}", data.getOrderId());
        requireLocalSteps().beginCancelOrder(data.getOrderId());
    }

    private void undoCancel(CancelOrderSagaData data) {
        logger.warn("CancelOrderSaga: undo cancellation for orderId={}", data.getOrderId());
        requireLocalSteps().undoCancelOrder(data.getOrderId());
    }

    private CommandWithDestination beginCancelTicket(CancelOrderSagaData data) {
        return send(new BeginCancelTicketCommand(data.getTicketId()))
            .to(ChannelNames.KITCHEN_SERVICE_COMMAND_CHANNEL)
            .build();
    }

    private CommandWithDestination undoCancelTicket(CancelOrderSagaData data) {
        return send(new UndoCancelTicketCommand(data.getTicketId()))
            .to(ChannelNames.KITCHEN_SERVICE_COMMAND_CHANNEL)
            .build();
    }

    /** Pivot: one deterministic financial action based on durable payment state. */
    private CommandWithDestination settlePayment(CancelOrderSagaData data) {
        if (data.getPaymentState() == null) {
            throw new IllegalStateException("Cancellation payment state is unknown");
        }
        return switch (data.getPaymentState()) {
            case AUTHORIZED -> send(new VoidAuthorizationCommand(
                data.getOrderId(),
                data.getAuthorizationId(),
                "ORDER_CANCELLED",
                voidRequestId(data)
            ))
                .to(ChannelNames.ACCOUNTING_SERVICE_COMMAND_CHANNEL)
                .build();
            case CAPTURED -> {
                if (data.getCaptureId() == null) {
                    throw new IllegalStateException("Captured payment is missing capture ID");
                }
                yield send(new RefundPaymentCommand(
                    data.getOrderId(),
                    data.getAuthorizationId(),
                    data.getOrderTotal(),
                    "ORDER_CANCELLED",
                    refundRequestId(data)
                ))
                    .to(ChannelNames.ACCOUNTING_SERVICE_COMMAND_CHANNEL)
                    .build();
            }
            case VOIDED, REFUNDED -> send(
                new CancelOrderSagaLocalSteps.NoopFinancialSettlementCommand(
                    data.getOrderId(),
                    data.getPaymentState()
                )
            )
                .to(ChannelNames.ORDER_SERVICE_COMMAND_CHANNEL)
                .build();
            case MANUAL_REVIEW, CAPTURE_PENDING, REFUND_PENDING, FAILED ->
                throw new IllegalStateException(
                    "Cancellation paused for financial state " + data.getPaymentState());
        };
    }

    /** Legacy reflection target retained while older tests migrate. */
    private CommandWithDestination reverseAuthorization(CancelOrderSagaData data) {
        return settlePayment(data);
    }

    private void recordFinancialSettlement(CancelOrderSagaData data) {
        requireLocalSteps().recordFinancialSettlement(
            data.getOrderId(),
            data.getPaymentState(),
            data.getAuthorizationId(),
            data.getCaptureId()
        );
    }

    private CommandWithDestination confirmCancelTicket(CancelOrderSagaData data) {
        return send(new ConfirmCancelTicketCommand(data.getTicketId()))
            .to(ChannelNames.KITCHEN_SERVICE_COMMAND_CHANNEL)
            .build();
    }

    private CommandWithDestination confirmCancelStep(CancelOrderSagaData data) {
        return send(new CancelOrderSagaLocalSteps.ConfirmCancelCommand(data.getOrderId()))
            .to(ChannelNames.CANCEL_ORDER_SAGA_COMMAND_CHANNEL)
            .build();
    }

    private String voidRequestId(CancelOrderSagaData data) {
        return "cancel-order-" + data.getOrderId()
            + "-authorization-" + data.getAuthorizationId() + "-void";
    }

    private String refundRequestId(CancelOrderSagaData data) {
        return "cancel-order-" + data.getOrderId()
            + "-capture-" + data.getCaptureId() + "-refund";
    }

    private CancelOrderSagaLocalSteps requireLocalSteps() {
        if (localSteps == null) {
            throw new IllegalStateException(
                "CancelOrderSagaLocalSteps is required to execute CancelOrderSaga");
        }
        return localSteps;
    }
}
