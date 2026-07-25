package net.ftgo.order.saga;

import io.eventuate.tram.commands.consumer.CommandHandlers;
import io.eventuate.tram.sagas.participant.SagaCommandHandlersBuilder;
import net.ftgo.common.channels.ChannelNames;

/** Consolidated local Order Service saga participant command registry. */
public class OrderSagaCommandHandlers {

    private final CreateOrderSagaLocalSteps createOrderSteps;
    private final CancelOrderSagaLocalSteps cancelOrderSteps;
    private final ReviseOrderSagaLocalSteps reviseOrderSteps;
    private final CapturePaymentSagaLocalSteps capturePaymentSteps;

    public OrderSagaCommandHandlers(
        CreateOrderSagaLocalSteps createOrderSteps,
        CancelOrderSagaLocalSteps cancelOrderSteps,
        ReviseOrderSagaLocalSteps reviseOrderSteps,
        CapturePaymentSagaLocalSteps capturePaymentSteps
    ) {
        this.createOrderSteps = createOrderSteps;
        this.cancelOrderSteps = cancelOrderSteps;
        this.reviseOrderSteps = reviseOrderSteps;
        this.capturePaymentSteps = capturePaymentSteps;
    }

    public CommandHandlers commandHandlers() {
        return SagaCommandHandlersBuilder
            .fromChannel(ChannelNames.ORDER_SERVICE_COMMAND_CHANNEL)
            .onMessage(CreateOrderSagaLocalSteps.RejectOrderCommand.class, createOrderSteps::rejectOrder)
            .onMessage(CreateOrderSagaLocalSteps.ApproveOrderCommand.class, createOrderSteps::approveOrder)
            .onMessage(CancelOrderSagaLocalSteps.BeginCancelCommand.class, cancelOrderSteps::beginCancel)
            .onMessage(CancelOrderSagaLocalSteps.UndoCancelCommand.class, cancelOrderSteps::undoCancel)
            .onMessage(CancelOrderSagaLocalSteps.ConfirmCancelCommand.class, cancelOrderSteps::confirmCancel)
            .onMessage(ReviseOrderSagaLocalSteps.BeginReviseCommand.class, reviseOrderSteps::beginRevise)
            .onMessage(ReviseOrderSagaLocalSteps.UndoReviseCommand.class, reviseOrderSteps::undoRevise)
            .onMessage(ReviseOrderSagaLocalSteps.ConfirmReviseCommand.class, reviseOrderSteps::confirmRevise)
            .onMessage(
                CapturePaymentSagaLocalSteps.FailPaymentCaptureCommand.class,
                capturePaymentSteps::failPaymentCapture
            )
            .build();
    }
}
