package net.ftgo.order.saga;

import io.eventuate.tram.commands.common.Command;
import io.eventuate.tram.commands.consumer.CommandHandlers;
import io.eventuate.tram.commands.consumer.CommandWithDestination;
import net.ftgo.accounting.messaging.AccountingServiceCommandHandlers;
import net.ftgo.accounting.repository.AccountRepository;
import net.ftgo.common.Money;
import net.ftgo.common.channels.ChannelNames;
import net.ftgo.consumer.messaging.ConsumerCommandHandlers;
import net.ftgo.consumer.service.ConsumerService;
import net.ftgo.kitchen.messaging.KitchenServiceCommandHandlers;
import net.ftgo.kitchen.messaging.DomainEventPublisher;
import net.ftgo.kitchen.repository.TicketRepository;
import net.ftgo.order.domain.OrderLineItem;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class CreateOrderSagaSharedContractTest {

    @Test
    void realParticipantHandlersAcceptTheCommandTypesSentByCreateOrderSaga() throws Exception {
        CreateOrderSaga saga = new CreateOrderSaga();
        CreateOrderSagaData data = new CreateOrderSagaData(
                1L,
                100L,
                200L,
                List.of(new OrderLineItem(10L, "Burger", new Money(BigDecimal.valueOf(12.99)), 2)),
                new Money(BigDecimal.valueOf(25.98))
        );
        data.setTicketId(300L);

        assertParticipantAccepts(
                commandSentBy(saga, "verifyConsumer", data),
                ChannelNames.CONSUMER_SERVICE_COMMAND_CHANNEL,
                new ConsumerCommandHandlers(mock(ConsumerService.class)).commandHandlers()
        );
        assertParticipantAccepts(
                commandSentBy(saga, "createTicket", data),
                ChannelNames.KITCHEN_SERVICE_COMMAND_CHANNEL,
                new KitchenServiceCommandHandlers(mock(TicketRepository.class), mock(DomainEventPublisher.class)).commandHandlers()
        );
        assertParticipantAccepts(
                commandSentBy(saga, "cancelTicket", data),
                ChannelNames.KITCHEN_SERVICE_COMMAND_CHANNEL,
                new KitchenServiceCommandHandlers(mock(TicketRepository.class), mock(DomainEventPublisher.class)).commandHandlers()
        );
        assertParticipantAccepts(
                commandSentBy(saga, "authorizeCard", data),
                ChannelNames.ACCOUNTING_SERVICE_COMMAND_CHANNEL,
                new AccountingServiceCommandHandlers(
                        mock(AccountRepository.class),
                        mock(net.ftgo.accounting.messaging.DomainEventPublisher.class)
                ).commandHandlers()
        );
        assertParticipantAccepts(
                commandSentBy(saga, "approveTicket", data),
                ChannelNames.KITCHEN_SERVICE_COMMAND_CHANNEL,
                new KitchenServiceCommandHandlers(mock(TicketRepository.class), mock(DomainEventPublisher.class)).commandHandlers()
        );
    }

    private CommandWithDestination commandSentBy(CreateOrderSaga saga, String methodName, CreateOrderSagaData data)
            throws Exception {
        Method method = CreateOrderSaga.class.getDeclaredMethod(methodName, CreateOrderSagaData.class);
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
