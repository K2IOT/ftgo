package net.ftgo.common.orderflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.ftgo.common.orderflow.commands.BeginCancelTicketCommand;
import net.ftgo.common.orderflow.commands.BeginReviseTicketCommand;
import net.ftgo.common.orderflow.commands.ConfirmReviseTicketCommand;
import net.ftgo.common.orderflow.commands.CreateTicketCommand;
import net.ftgo.common.orderflow.commands.ReverseAuthorizationCommand;
import net.ftgo.common.orderflow.commands.ReviseAuthorizationCommand;
import net.ftgo.common.orderflow.replies.TicketCancellationRefused;
import net.ftgo.common.orderflow.replies.TicketRevisionRefused;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderFlowSharedContractsSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void reverseAuthorizationCommandRoundTrips() throws Exception {
        ReverseAuthorizationCommand command = new ReverseAuthorizationCommand(12L, 34L);
        String json = objectMapper.writeValueAsString(command);
        ReverseAuthorizationCommand parsed = objectMapper.readValue(json, ReverseAuthorizationCommand.class);

        assertEquals(12L, parsed.getConsumerId());
        assertEquals(34L, parsed.getAuthorizationId());
    }

    @Test
    void reviseAuthorizationCommandRoundTripsWithStableRequestId() throws Exception {
        ReviseAuthorizationCommand command = new ReviseAuthorizationCommand(
            12L, 34L, new BigDecimal("45.67"), "revise-request-1"
        );
        String json = objectMapper.writeValueAsString(command);
        ReviseAuthorizationCommand parsed = objectMapper.readValue(json, ReviseAuthorizationCommand.class);

        assertEquals(12L, parsed.getConsumerId());
        assertEquals(34L, parsed.getAuthorizationId());
        assertEquals(new BigDecimal("45.67"), parsed.getNewAmount());
        assertEquals("revise-request-1", parsed.getRequestId());
    }

    @Test
    void beginAndConfirmReviseTicketCommandsRoundTrip() throws Exception {
        BeginReviseTicketCommand begin = new BeginReviseTicketCommand(
            99L,
            List.of(
                new CreateTicketCommand.TicketLineItemDTO(1L, "Burger", 2),
                new CreateTicketCommand.TicketLineItemDTO(2L, "Fries", 1)
            )
        );
        String beginJson = objectMapper.writeValueAsString(begin);
        BeginReviseTicketCommand parsedBegin = objectMapper.readValue(beginJson, BeginReviseTicketCommand.class);

        assertEquals(99L, parsedBegin.getTicketId());
        assertEquals(2, parsedBegin.getRevisedLineItems().size());
        assertEquals("Burger", parsedBegin.getRevisedLineItems().get(0).getName());

        ConfirmReviseTicketCommand confirm = new ConfirmReviseTicketCommand(99L);
        String confirmJson = objectMapper.writeValueAsString(confirm);
        ConfirmReviseTicketCommand parsedConfirm = objectMapper.readValue(confirmJson, ConfirmReviseTicketCommand.class);
        assertEquals(99L, parsedConfirm.getTicketId());
    }

    @Test
    void beginCancelTicketCommandAndRefusalRepliesRoundTrip() throws Exception {
        BeginCancelTicketCommand beginCancel = new BeginCancelTicketCommand(77L);
        String beginCancelJson = objectMapper.writeValueAsString(beginCancel);
        BeginCancelTicketCommand parsedBeginCancel =
            objectMapper.readValue(beginCancelJson, BeginCancelTicketCommand.class);
        assertEquals(77L, parsedBeginCancel.getTicketId());

        TicketCancellationRefused cancellationRefused = new TicketCancellationRefused(
            TicketCancellationRefused.PREPARATION_ALREADY_STARTED,
            "Already preparing"
        );
        String cancellationJson = objectMapper.writeValueAsString(cancellationRefused);
        TicketCancellationRefused parsedCancellation =
            objectMapper.readValue(cancellationJson, TicketCancellationRefused.class);
        assertEquals(TicketCancellationRefused.PREPARATION_ALREADY_STARTED, parsedCancellation.getReasonCode());

        TicketRevisionRefused revisionRefused = new TicketRevisionRefused(
            TicketRevisionRefused.PREPARATION_ALREADY_STARTED,
            "Already preparing"
        );
        String revisionJson = objectMapper.writeValueAsString(revisionRefused);
        TicketRevisionRefused parsedRevision = objectMapper.readValue(revisionJson, TicketRevisionRefused.class);
        assertEquals(TicketRevisionRefused.PREPARATION_ALREADY_STARTED, parsedRevision.getReasonCode());
    }
}
