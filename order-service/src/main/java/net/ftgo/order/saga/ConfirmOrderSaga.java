package net.ftgo.order.saga;

import io.eventuate.tram.commands.consumer.CommandWithDestination;
import io.eventuate.tram.sagas.orchestration.SagaDefinition;
import io.eventuate.tram.sagas.simpledsl.SimpleSaga;
import net.ftgo.common.channels.ChannelNames;
import net.ftgo.common.orderflow.commands.CaptureAuthorizationCommand;
import net.ftgo.common.orderflow.commands.CommitConsumerCreditCommand;

import static io.eventuate.tram.commands.consumer.CommandWithDestinationBuilder.send;

/**
 * Completes an accepted order.
 *
 * <p>Payment capture is the pivot. All following actions are idempotent and
 * retried forward until the order reaches APPROVED.</p>
 */
public class ConfirmOrderSaga implements SimpleSaga<ConfirmOrderSagaData> {

    private final ConfirmOrderSagaLocalSteps localSteps;
    private final SagaDefinition<ConfirmOrderSagaData> sagaDefinition;

    public ConfirmOrderSaga() {
        this(null);
    }

    public ConfirmOrderSaga(ConfirmOrderSagaLocalSteps localSteps) {
        this.localSteps = localSteps;
        this.sagaDefinition = step()
            .invokeParticipant(this::captureAuthorization)
        .step()
            .invokeParticipant(this::commitCredit)
        .step()
            .invokeLocal(this::confirmOrder)
        .build();
    }

    @Override
    public SagaDefinition<ConfirmOrderSagaData> getSagaDefinition() {
        return sagaDefinition;
    }

    private CommandWithDestination captureAuthorization(ConfirmOrderSagaData data) {
        return send(new CaptureAuthorizationCommand(
            data.getOrderId(),
            data.getAuthorizationId(),
            requestId(data, "capture")
        ))
            .to(ChannelNames.ACCOUNTING_SERVICE_COMMAND_CHANNEL)
            .build();
    }

    private CommandWithDestination commitCredit(ConfirmOrderSagaData data) {
        return send(new CommitConsumerCreditCommand(
            data.getConsumerId(),
            data.getOrderId()
        ))
            .to(ChannelNames.CONSUMER_SERVICE_COMMAND_CHANNEL)
            .build();
    }

    private void confirmOrder(ConfirmOrderSagaData data) {
        requireLocalSteps().confirmOrder(data.getOrderId());
    }

    private ConfirmOrderSagaLocalSteps requireLocalSteps() {
        if (localSteps == null) {
            throw new IllegalStateException(
                "ConfirmOrderSagaLocalSteps is required to execute ConfirmOrderSaga"
            );
        }
        return localSteps;
    }

    private String requestId(ConfirmOrderSagaData data, String operation) {
        return "order-" + data.getOrderId() + "-" + operation;
    }
}
