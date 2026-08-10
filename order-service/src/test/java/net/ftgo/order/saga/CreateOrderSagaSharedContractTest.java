package net.ftgo.order.saga;

import io.eventuate.tram.commands.common.Command;
import io.eventuate.tram.commands.consumer.CommandHandlers;
import io.eventuate.tram.commands.consumer.CommandWithDestination;
import net.ftgo.accounting.messaging.AccountingServiceCommandHandlers;
import net.ftgo.accounting.payment.PaymentAuthorizationGateway;
import net.ftgo.accounting.repository.AccountRepository;
import net.ftgo.accounting.settlement.PaymentLedgerService;
import net.ftgo.accounting.settlement.SettlementGateway;
import net.ftgo.accounting.settlement.SettlementReconciliationWorkRepository;
import net.ftgo.common.Money;
import net.ftgo.common.channels.ChannelNames;
import net.ftgo.common.messaging.IdempotentCommandExecutor;
import net.ftgo.consumer.messaging.ConsumerCommandHandlers;
import net.ftgo.consumer.service.ConsumerService;
import net.ftgo.consumer.service.CreditReservationService;
import net.ftgo.kitchen.messaging.DomainEventPublisher;
import net.ftgo.kitchen.messaging.KitchenServiceCommandHandlers;
import net.ftgo.kitchen.repository.TicketRepository;
import net.ftgo.order.domain.OrderLineItem;
import net.ftgo.restaurant.messaging.RestaurantOrderCommandHandlers;
import net.ftgo.restaurant.service.OrderMenuValidationService;
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
    void realParticipantHandlersAcceptEveryCommandSentByCreateOrderSaga() throws Exception {
        CreateOrderSaga saga = new CreateOrderSaga();
        CreateOrderSagaData data = new CreateOrderSagaData(
            1L,
            100L,
            200L,
            List.of(new OrderLineItem(10L, "Burger", new Money(BigDecimal.valueOf(12.99)), 2)),
            new Money(BigDecimal.valueOf(25.98)),
            7L
        );
        data.setTicketId(300L);
        data.setAuthorizationId(400L);

        CommandHandlers restaurantHandlers = new RestaurantOrderCommandHandlers(
            mock(OrderMenuValidationService.class)
        ).commandHandlers();
        CommandHandlers consumerHandlers = new ConsumerCommandHandlers(
            mock(ConsumerService.class),
            mock(CreditReservationService.class),
            mock(IdempotentCommandExecutor.class)
        ).commandHandlers();
        CommandHandlers kitchenHandlers = new KitchenServiceCommandHandlers(
            mock(TicketRepository.class),
            mock(DomainEventPublisher.class),
            mock(IdempotentCommandExecutor.class)
        ).commandHandlers();
        CommandHandlers accountingHandlers = new AccountingServiceCommandHandlers(
            mock(AccountRepository.class),
            mock(net.ftgo.accounting.messaging.DomainEventPublisher.class),
            mock(PaymentAuthorizationGateway.class),
            mock(SettlementGateway.class),
            mock(PaymentLedgerService.class),
            mock(SettlementReconciliationWorkRepository.class),
            mock(IdempotentCommandExecutor.class)
        ).commandHandlers();

        assertParticipantAccepts(
            commandSentBy(saga, "validateMenu", data),
            ChannelNames.RESTAURANT_SERVICE_COMMAND_CHANNEL,
            restaurantHandlers
        );
        assertParticipantAccepts(
            commandSentBy(saga, "reserveCredit", data),
            ChannelNames.CONSUMER_SERVICE_COMMAND_CHANNEL,
            consumerHandlers
        );
        assertParticipantAccepts(
            commandSentBy(saga, "releaseCredit", data),
            ChannelNames.CONSUMER_SERVICE_COMMAND_CHANNEL,
            consumerHandlers
        );
        assertParticipantAccepts(
            commandSentBy(saga, "createTicket", data),
            ChannelNames.KITCHEN_SERVICE_COMMAND_CHANNEL,
            kitchenHandlers
        );
        assertParticipantAccepts(
            commandSentBy(saga, "cancelTicket", data),
            ChannelNames.KITCHEN_SERVICE_COMMAND_CHANNEL,
            kitchenHandlers
        );
        assertParticipantAccepts(
            commandSentBy(saga, "authorizeCard", data),
            ChannelNames.ACCOUNTING_SERVICE_COMMAND_CHANNEL,
            accountingHandlers
        );
        assertParticipantAccepts(
            commandSentBy(saga, "voidAuthorization", data),
            ChannelNames.ACCOUNTING_SERVICE_COMMAND_CHANNEL,
            accountingHandlers
        );
        assertParticipantAccepts(
            commandSentBy(saga, "approveTicket", data),
            ChannelNames.KITCHEN_SERVICE_COMMAND_CHANNEL,
            kitchenHandlers
        );
    }

    private CommandWithDestination commandSentBy(
        CreateOrderSaga saga,
        String methodName,
        CreateOrderSagaData data
    ) throws Exception {
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