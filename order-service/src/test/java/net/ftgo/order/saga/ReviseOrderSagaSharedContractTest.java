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
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class ReviseOrderSagaSharedContractTest {

    @Test
    void kitchenParticipantHandlersAcceptTheReviseCommandsSentByReviseOrderSaga() throws Exception {
        ReviseOrderSaga saga = new ReviseOrderSaga();
        ReviseOrderSagaData data = new ReviseOrderSagaData(
            1L,
            100L,
            List.of(),
            net.ftgo.common.Money.ZERO,
            200L,
            300L,
            "revision-request-shared-contract"
        );

        CommandHandlers kitchenHandlers = new KitchenServiceCommandHandlers(
            mock(TicketRepository.class),
            mock(DomainEventPublisher.class),
            mock(IdempotentCommandExecutor.class)
        ).commandHandlers();

        assertParticipantAccepts(commandSentBy(saga, "beginReviseTicket", data),
            ChannelNames.KITCHEN_SERVICE_COMMAND_CHANNEL, kitchenHandlers);
        assertParticipantAccepts(commandSentBy(saga, "confirmReviseTicket", data),
            ChannelNames.KITCHEN_SERVICE_COMMAND_CHANNEL, kitchenHandlers);
        assertParticipantAccepts(commandSentBy(saga, "undoReviseTicket", data),
            ChannelNames.KITCHEN_SERVICE_COMMAND_CHANNEL, kitchenHandlers);
    }

    private CommandWithDestination commandSentBy(ReviseOrderSaga saga, String methodName, ReviseOrderSagaData data)
        throws Exception {
        Method method = ReviseOrderSaga.class.getDeclaredMethod(methodName, ReviseOrderSagaData.class);
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
