package net.ftgo.order.saga;

import io.eventuate.tram.commands.common.Command;
import io.eventuate.tram.commands.consumer.CommandHandlers;
import io.eventuate.tram.commands.consumer.CommandWithDestination;
import net.ftgo.accounting.messaging.AccountingServiceCommandHandlers;
import net.ftgo.accounting.messaging.DomainEventPublisher;
import net.ftgo.accounting.payment.PaymentAuthorizationGateway;
import net.ftgo.accounting.repository.AccountRepository;
import net.ftgo.accounting.settlement.PaymentLedgerService;
import net.ftgo.accounting.settlement.SettlementGateway;
import net.ftgo.accounting.settlement.SettlementReconciliationWorkRepository;
import net.ftgo.common.Money;
import net.ftgo.common.channels.ChannelNames;
import net.ftgo.common.messaging.IdempotentCommandExecutor;
import net.ftgo.order.domain.OrderLineItem;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class PaymentAuthorizationSharedContractTest {

    @Test
    void accountingParticipantHandlersAcceptPaymentCommandsSentByCancelAndReviseSagas() throws Exception {
        CancelOrderSaga cancelSaga = new CancelOrderSaga();
        CancelOrderSagaData cancelData = new CancelOrderSagaData(1L, 100L, 200L, 300L);

        ReviseOrderSaga reviseSaga = new ReviseOrderSaga();
        ReviseOrderSagaData reviseData = new ReviseOrderSagaData(
            1L,
            100L,
            List.of(new OrderLineItem(10L, "Burger", new Money("12.99"), 2)),
            new Money("25.98"),
            200L,
            300L,
            "revision-request-contract"
        );

        CommandHandlers accountingHandlers = new AccountingServiceCommandHandlers(
            mock(AccountRepository.class),
            mock(DomainEventPublisher.class),
            mock(PaymentAuthorizationGateway.class),
            mock(SettlementGateway.class),
            mock(PaymentLedgerService.class),
            mock(SettlementReconciliationWorkRepository.class),
            mock(IdempotentCommandExecutor.class)
        ).commandHandlers();

        assertParticipantAccepts(
            commandSentBy(cancelSaga, "reverseAuthorization", cancelData),
            ChannelNames.ACCOUNTING_SERVICE_COMMAND_CHANNEL,
            accountingHandlers
        );
        assertParticipantAccepts(
            commandSentBy(reviseSaga, "reviseCreditCardAuthorization", reviseData),
            ChannelNames.ACCOUNTING_SERVICE_COMMAND_CHANNEL,
            accountingHandlers
        );
    }

    private CommandWithDestination commandSentBy(Object saga, String methodName, Object data) throws Exception {
        Method method = saga.getClass().getDeclaredMethod(methodName, data.getClass());
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