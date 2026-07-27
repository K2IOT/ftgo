package net.ftgo.order.saga;

import io.eventuate.tram.commands.consumer.CommandWithDestination;
import io.eventuate.tram.sagas.orchestration.SagaDefinition;
import io.eventuate.tram.sagas.simpledsl.SimpleSaga;
import net.ftgo.common.channels.ChannelNames;
import net.ftgo.common.orderflow.commands.BeginCancelTicketCommand;
import net.ftgo.common.orderflow.commands.ConfirmCancelTicketCommand;
import net.ftgo.common.orderflow.commands.ReverseAuthorizationCommand;
import net.ftgo.common.orderflow.commands.UndoCancelTicketCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.eventuate.tram.commands.consumer.CommandWithDestinationBuilder.send;

/**
 * CancelOrderSaga orchestrates the distributed transaction for order cancellation.
 *
 * <p>The accounting pivot is settlement-aware. An uncaptured authorization is
 * voided; a captured or partially refunded payment is refunded for the remaining
 * captured amount. The command carries the order and request identities required
 * for provider and immutable-ledger idempotency.</p>
 */
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
            .invokeParticipant(this::reverseAuthorization)
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
        logger.info("CancelOrderSaga: Step 1 - beginCancel for orderId={}", data.getOrderId());
        requireLocalSteps().beginCancelOrder(data.getOrderId());
    }

    private void undoCancel(CancelOrderSagaData data) {
        logger.warn("CancelOrderSaga: Compensation - undoCancel for orderId={}", data.getOrderId());
        requireLocalSteps().undoCancelOrder(data.getOrderId());
    }

    private CancelOrderSagaLocalSteps requireLocalSteps() {
        if (localSteps == null) {
            throw new IllegalStateException(
                "CancelOrderSagaLocalSteps is required to execute CancelOrderSaga"
            );
        }
        return localSteps;
    }

    private CommandWithDestination beginCancelTicket(CancelOrderSagaData data) {
        logger.info(
            "CancelOrderSaga: Step 2 - beginCancelTicket for ticketId={}",
            data.getTicketId()
        );
        return send(new BeginCancelTicketCommand(data.getTicketId()))
            .to(ChannelNames.KITCHEN_SERVICE_COMMAND_CHANNEL)
            .build();
    }

    private CommandWithDestination undoCancelTicket(CancelOrderSagaData data) {
        logger.warn(
            "CancelOrderSaga: Compensation - undoCancelTicket for ticketId={}",
            data.getTicketId()
        );
        return send(new UndoCancelTicketCommand(data.getTicketId()))
            .to(ChannelNames.KITCHEN_SERVICE_COMMAND_CHANNEL)
            .build();
    }

    private CommandWithDestination reverseAuthorization(CancelOrderSagaData data) {
        logger.info(
            "CancelOrderSaga: Step 3 (PIVOT) - settle cancellation for authorizationId={}",
            data.getAuthorizationId()
        );
        return send(new ReverseAuthorizationCommand(
            data.getConsumerId(),
            data.getAuthorizationId(),
            data.getOrderId(),
            "order-" + data.getOrderId() + "-cancel-settlement"
        ))
            .to(ChannelNames.ACCOUNTING_SERVICE_COMMAND_CHANNEL)
            .build();
    }

    private CommandWithDestination confirmCancelTicket(CancelOrderSagaData data) {
        logger.info(
            "CancelOrderSaga: Step 4 (retriable) - confirmCancelTicket for ticketId={}",
            data.getTicketId()
        );
        return send(new ConfirmCancelTicketCommand(data.getTicketId()))
            .to(ChannelNames.KITCHEN_SERVICE_COMMAND_CHANNEL)
            .build();
    }

    private CommandWithDestination confirmCancelStep(CancelOrderSagaData data) {
        logger.info(
            "CancelOrderSaga: Step 5 (retriable) - confirmCancel for orderId={}",
            data.getOrderId()
        );
        return send(new CancelOrderSagaLocalSteps.ConfirmCancelCommand(data.getOrderId()))
            .to(ChannelNames.CANCEL_ORDER_SAGA_COMMAND_CHANNEL)
            .build();
    }
}