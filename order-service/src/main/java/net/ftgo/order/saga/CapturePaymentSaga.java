package net.ftgo.order.saga;

import io.eventuate.tram.commands.consumer.CommandWithDestination;
import io.eventuate.tram.sagas.orchestration.SagaDefinition;
import io.eventuate.tram.sagas.simpledsl.SimpleSaga;
import net.ftgo.common.channels.ChannelNames;
import net.ftgo.common.orderflow.commands.CaptureAuthorizationCommand;
import net.ftgo.common.orderflow.commands.CommitConsumerCreditCommand;
import net.ftgo.common.orderflow.commands.ConfirmTicketAcceptanceCommand;
import net.ftgo.common.orderflow.commands.ReleaseConsumerCreditCommand;
import net.ftgo.common.orderflow.commands.UndoTicketAcceptanceCommand;
import net.ftgo.common.orderflow.commands.VoidAuthorizationCommand;
import net.ftgo.common.orderflow.replies.PaymentCaptured;

import static io.eventuate.tram.commands.consumer.CommandWithDestinationBuilder.send;

/**
 * Captures the existing authorization after a restaurant acceptance request.
 * Provider transient failures remain pending and retry. A durable decline runs
 * compensation in reverse order: undo acceptance, void authorization, release
 * credit, then finalize the rejected order.
 */
public class CapturePaymentSaga implements SimpleSaga<CapturePaymentSagaData> {

    private final CapturePaymentSagaLocalSteps localSteps;
    private final SagaDefinition<CapturePaymentSagaData> sagaDefinition;

    public CapturePaymentSaga() {
        this(null);
    }

    public CapturePaymentSaga(CapturePaymentSagaLocalSteps localSteps) {
        this.localSteps = localSteps;
        this.sagaDefinition = step()
            .withCompensation(this::markPaymentFailed)
        .step()
            .withCompensation(this::releaseCredit)
        .step()
            .withCompensation(this::voidAuthorization)
        .step()
            .withCompensation(this::undoAcceptance)
        .step()
            .invokeParticipant(this::captureAuthorization)
            .onReply(PaymentCaptured.class, this::handlePaymentCaptured)
        .step()
            .invokeLocal(this::recordPaymentCaptured)
        .step()
            .invokeParticipant(this::confirmAcceptance)
        .step()
            .invokeParticipant(this::commitCredit)
        .step()
            .invokeLocal(this::completeOrder)
        .build();
    }

    @Override
    public SagaDefinition<CapturePaymentSagaData> getSagaDefinition() {
        return sagaDefinition;
    }

    private CommandWithDestination markPaymentFailed(CapturePaymentSagaData data) {
        return send(new CapturePaymentSagaLocalSteps.FailPaymentCaptureCommand(
            data.getOrderId(),
            failureReason(data)
        ))
            .to(ChannelNames.ORDER_SERVICE_COMMAND_CHANNEL)
            .build();
    }

    private CommandWithDestination releaseCredit(CapturePaymentSagaData data) {
        return send(new ReleaseConsumerCreditCommand(
            data.getConsumerId(),
            data.getOrderId(),
            failureReason(data)
        ))
            .to(ChannelNames.CONSUMER_SERVICE_COMMAND_CHANNEL)
            .build();
    }

    private CommandWithDestination voidAuthorization(CapturePaymentSagaData data) {
        return send(new VoidAuthorizationCommand(
            data.getOrderId(),
            data.getAuthorizationId(),
            failureReason(data),
            captureRequestId(data) + "-void"
        ))
            .to(ChannelNames.ACCOUNTING_SERVICE_COMMAND_CHANNEL)
            .build();
    }

    private CommandWithDestination undoAcceptance(CapturePaymentSagaData data) {
        return send(new UndoTicketAcceptanceCommand(
            data.getTicketId(),
            failureReason(data)
        ))
            .to(ChannelNames.KITCHEN_SERVICE_COMMAND_CHANNEL)
            .build();
    }

    private CommandWithDestination captureAuthorization(CapturePaymentSagaData data) {
        return send(new CaptureAuthorizationCommand(
            data.getOrderId(),
            data.getAuthorizationId(),
            captureRequestId(data)
        ))
            .to(ChannelNames.ACCOUNTING_SERVICE_COMMAND_CHANNEL)
            .build();
    }

    private void handlePaymentCaptured(CapturePaymentSagaData data, PaymentCaptured reply) {
        data.setCaptureId(reply.getCaptureId());
    }

    private void recordPaymentCaptured(CapturePaymentSagaData data) {
        requireLocalSteps().recordPaymentCaptured(
            data.getOrderId(),
            data.getCaptureId(),
            captureRequestId(data)
        );
    }

    private CommandWithDestination confirmAcceptance(CapturePaymentSagaData data) {
        return send(new ConfirmTicketAcceptanceCommand(
            data.getTicketId(),
            captureRequestId(data)
        ))
            .to(ChannelNames.KITCHEN_SERVICE_COMMAND_CHANNEL)
            .build();
    }

    private CommandWithDestination commitCredit(CapturePaymentSagaData data) {
        return send(new CommitConsumerCreditCommand(
            data.getConsumerId(),
            data.getOrderId()
        ))
            .to(ChannelNames.CONSUMER_SERVICE_COMMAND_CHANNEL)
            .build();
    }

    private void completeOrder(CapturePaymentSagaData data) {
        requireLocalSteps().completeOrder(data.getOrderId());
    }

    private String captureRequestId(CapturePaymentSagaData data) {
        return "capture-order-" + data.getOrderId()
            + "-authorization-" + data.getAuthorizationId();
    }

    private String failureReason(CapturePaymentSagaData data) {
        return data.getFailureCode() == null || data.getFailureCode().isBlank()
            ? "PAYMENT_CAPTURE_FAILED"
            : data.getFailureCode();
    }

    private CapturePaymentSagaLocalSteps requireLocalSteps() {
        if (localSteps == null) {
            throw new IllegalStateException(
                "CapturePaymentSagaLocalSteps is required to execute CapturePaymentSaga");
        }
        return localSteps;
    }
}
