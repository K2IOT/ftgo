package net.ftgo.order.saga;

import io.eventuate.tram.commands.common.Command;
import io.eventuate.tram.commands.consumer.CommandHandlers;
import io.eventuate.tram.commands.consumer.CommandWithDestination;
import net.ftgo.common.channels.ChannelNames;
import net.ftgo.common.messaging.IdempotentCommandExecutor;
import net.ftgo.kitchen.messaging.DomainEventPublisher;
import net.ftgo.kitchen.messaging.KitchenServiceCommandHandlers;
import net.ftgo.kitchen.repository.TicketRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class CancelOrderSagaSharedContractTest {

    @Test
    void kitchenParticipantHandlersAcceptTheCancelCommandsSentByCancelOrderSaga() throws Exception {
        CancelOrderSaga saga = new CancelOrderSaga();
        CancelOrderSagaData data = new CancelOrderSagaData(1L, 100L, 200L, 300L);

        CommandHandlers kitchenHandlers = new KitchenServiceCommandHandlers(
            mock(TicketRepository.class),
            mock(DomainEventPublisher.class),
            mock(IdempotentCommandExecutor.class)
        ).commandHandlers();

        assertParticipantAccepts(
            commandSentBy(saga, "beginCancelTicket", data),
            ChannelNames.KITCHEN_SERVICE_COMMAND_CHANNEL,
            kitchenHandlers
        );
        assertParticipantAccepts(
            commandSentBy(saga, "confirmCancelTicket", data),
            ChannelNames.KITCHEN_SERVICE_COMMAND_CHANNEL,
            kitchenHandlers
        );
        assertParticipantAccepts(
            commandSentBy(saga, "undoCancelTicket", data),
            ChannelNames.KITCHEN_SERVICE_COMMAND_CHANNEL,
            kitchenHandlers
        );
    }

    private CommandWithDestination commandSentBy(CancelOrderSaga saga, String methodName, CancelOrderSagaData data)
        throws Exception {
        Method method = CancelOrderSaga.class.getDeclaredMethod(methodName, CancelOrderSagaData.class);
        method.setAccessible(true);
        return (CommandWithDestination) method.invoke(saga, data);
    }

    private void assertParticipantAccepts(
        CommandWithDestination sentCommand,
        String expectedChannel,
        CommandHandlers participantHandlers
    ) {
        assertEquals(expectedChannel, sentCommand.getDestinationChannel());

        Class<? extends Command> sentCommandType = sentCommand.getCommand().getClass();
        Set<Class<?>> acceptedCommandTypes = participantHandlers.getHandlers().stream()
            .map(handler -> (Class<?>) handler.getCommandClass())
            .collect(Collectors.toSet());

        assertTrue(
            acceptedCommandTypes.contains(sentCommandType),
            () -> "Participant handlers did not accept " + sentCommandType.getName()
                + ". Accepted: " + acceptedCommandTypes
        );
    }
}
