package net.ftgo.order.saga;

import io.eventuate.tram.commands.consumer.CommandWithDestination;
import net.ftgo.common.channels.ChannelNames;
import net.ftgo.common.orderflow.commands.CaptureAuthorizationCommand;
import net.ftgo.common.orderflow.commands.CommitConsumerCreditCommand;
import net.ftgo.common.orderflow.commands.ConfirmTicketAcceptanceCommand;
import net.ftgo.common.orderflow.commands.ReleaseConsumerCreditCommand;
import net.ftgo.common.orderflow.commands.UndoTicketAcceptanceCommand;
import net.ftgo.common.orderflow.commands.VoidAuthorizationCommand;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class CapturePaymentSagaIntegrationTest {

    private final CapturePaymentSaga saga = new CapturePaymentSaga();
    private final CapturePaymentSagaData data = new CapturePaymentSagaData(
        101L,
        301L,
        901L,
        501L,
        601L,
        "accept-ticket-901"
    );

    @Test
    void forwardPathUsesOneStableCaptureRequest() throws Exception {
        CommandWithDestination capture = command("captureAuthorization");
        CommandWithDestination confirm = command("confirmAcceptance");
        CommandWithDestination commit = command("commitCredit");

        assertThat(capture.getDestinationChannel())
            .isEqualTo(ChannelNames.ACCOUNTING_SERVICE_COMMAND_CHANNEL);
        CaptureAuthorizationCommand captureCommand =
            (CaptureAuthorizationCommand) capture.getCommand();
        assertThat(captureCommand.getRequestId())
            .isEqualTo("capture-order-101-authorization-501");

        assertThat(confirm.getDestinationChannel())
            .isEqualTo(ChannelNames.KITCHEN_SERVICE_COMMAND_CHANNEL);
        ConfirmTicketAcceptanceCommand confirmCommand =
            (ConfirmTicketAcceptanceCommand) confirm.getCommand();
        assertThat(confirmCommand.getTicketId()).isEqualTo(901L);
        assertThat(confirmCommand.getCaptureRequestId())
            .isEqualTo("capture-order-101-authorization-501");

        assertThat(commit.getDestinationChannel())
            .isEqualTo(ChannelNames.CONSUMER_SERVICE_COMMAND_CHANNEL);
        assertThat(commit.getCommand()).isInstanceOf(CommitConsumerCreditCommand.class);
    }

    @Test
    void declineCompensationUsesStableIdsAndRequiredOrder() throws Exception {
        CommandWithDestination undo = command("undoAcceptance");
        CommandWithDestination voidAuthorization = command("voidAuthorization");
        CommandWithDestination release = command("releaseCredit");

        assertThat(undo.getCommand()).isInstanceOf(UndoTicketAcceptanceCommand.class);
        assertThat(undo.getDestinationChannel())
            .isEqualTo(ChannelNames.KITCHEN_SERVICE_COMMAND_CHANNEL);

        VoidAuthorizationCommand voidCommand =
            (VoidAuthorizationCommand) voidAuthorization.getCommand();
        assertThat(voidCommand.getRequestId())
            .isEqualTo("capture-order-101-authorization-501-void");
        assertThat(voidAuthorization.getDestinationChannel())
            .isEqualTo(ChannelNames.ACCOUNTING_SERVICE_COMMAND_CHANNEL);

        assertThat(release.getCommand()).isInstanceOf(ReleaseConsumerCreditCommand.class);
        assertThat(release.getDestinationChannel())
            .isEqualTo(ChannelNames.CONSUMER_SERVICE_COMMAND_CHANNEL);
    }

    private CommandWithDestination command(String methodName) throws Exception {
        Method method = CapturePaymentSaga.class.getDeclaredMethod(
            methodName,
            CapturePaymentSagaData.class
        );
        method.setAccessible(true);
        return (CommandWithDestination) method.invoke(saga, data);
    }
}
