package net.ftgo.order.saga;

import io.eventuate.tram.commands.consumer.CommandHandlers;
import io.eventuate.tram.sagas.participant.SagaCommandHandlersBuilder;
import net.ftgo.common.channels.ChannelNames;

/**
 * Owns the single Order Service command subscription used by all local saga
 * participants. A Kafka command channel must have one dispatcher handler set;
 * registering independent dispatcher groups on the same channel causes every
 * group to receive commands it cannot handle.
 */
public class OrderSagaCommandHandlers {

    private final CreateOrderSagaLocalSteps createOrderSteps;
    private final CancelOrderSagaLocalSteps cancelOrderSteps;
    private final ReviseOrderSagaLocalSteps reviseOrderSteps;

    public OrderSagaCommandHandlers(CreateOrderSagaLocalSteps createOrderSteps,
                                    CancelOrderSagaLocalSteps cancelOrderSteps,
                                    ReviseOrderSagaLocalSteps reviseOrderSteps) {
        this.createOrderSteps = createOrderSteps;
        this.cancelOrderSteps = cancelOrderSteps;
        this.reviseOrderSteps = reviseOrderSteps;
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
            .build();
    }
}
