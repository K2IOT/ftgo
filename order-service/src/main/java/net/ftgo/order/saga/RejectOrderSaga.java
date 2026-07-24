package net.ftgo.order.saga;

import io.eventuate.tram.commands.consumer.CommandWithDestination;
import io.eventuate.tram.sagas.orchestration.SagaDefinition;
import io.eventuate.tram.sagas.simpledsl.SimpleSaga;
import net.ftgo.common.channels.ChannelNames;
import net.ftgo.common.orderflow.commands.ReleaseConsumerCreditCommand;
import net.ftgo.common.orderflow.commands.VoidAuthorizationCommand;

import static io.eventuate.tram.commands.consumer.CommandWithDestinationBuilder.send;

/**
 * Releases prepared resources after explicit restaurant rejection or timeout.
 */
public class RejectOrderSaga implements SimpleSaga<RejectOrderSagaData> {

    private final RejectOrderSagaLocalSteps localSteps;
    private final SagaDefinition<RejectOrderSagaData> sagaDefinition;

    public RejectOrderSaga() {
        this(null);
    }

    public RejectOrderSaga(RejectOrderSagaLocalSteps localSteps) {
        this.localSteps = localSteps;
        this.sagaDefinition = step()
            .invokeParticipant(this::voidAuthorization)
        .step()
            .invokeParticipant(this::releaseCredit)
        .step()
            .invokeLocal(this::rejectOrder)
        .build();
    }

    @Override
    public SagaDefinition<RejectOrderSagaData> getSagaDefinition() {
        return sagaDefinition;
    }

    private CommandWithDestination voidAuthorization(RejectOrderSagaData data) {
        return send(new VoidAuthorizationCommand(
            data.getOrderId(),
            data.getAuthorizationId(),
            data.getFailureCode(),
            requestId(data, "void")
        ))
            .to(ChannelNames.ACCOUNTING_SERVICE_COMMAND_CHANNEL)
            .build();
    }

    private CommandWithDestination releaseCredit(RejectOrderSagaData data) {
        return send(new ReleaseConsumerCreditCommand(
            data.getConsumerId(),
            data.getOrderId(),
            data.getFailureCode()
        ))
            .to(ChannelNames.CONSUMER_SERVICE_COMMAND_CHANNEL)
            .build();
    }

    private void rejectOrder(RejectOrderSagaData data) {
        requireLocalSteps().rejectOrder(data.getOrderId());
    }

    private RejectOrderSagaLocalSteps requireLocalSteps() {
        if (localSteps == null) {
            throw new IllegalStateException(
                "RejectOrderSagaLocalSteps is required to execute RejectOrderSaga"
            );
        }
        return localSteps;
    }

    private String requestId(RejectOrderSagaData data, String operation) {
        return "order-" + data.getOrderId() + "-" + operation;
    }
}
