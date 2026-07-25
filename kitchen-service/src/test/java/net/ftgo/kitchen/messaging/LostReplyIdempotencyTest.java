package net.ftgo.kitchen.messaging;

import io.eventuate.tram.commands.consumer.CommandMessage;
import io.eventuate.tram.messaging.common.Message;
import net.ftgo.common.messaging.IdempotentCommandExecutor;
import net.ftgo.common.orderflow.commands.CreateTicketCommand;
import net.ftgo.kitchen.domain.Ticket;
import net.ftgo.kitchen.repository.TicketRepository;
import net.ftgo.testsupport.IdempotentCommandContract;
import net.ftgo.testsupport.InMemoryProcessedCommandStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LostReplyIdempotencyTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private DomainEventPublisher eventPublisher;

    @Test
    void duplicateCreateTicketCommandReplaysEstablishedTicketReply() {
        IdempotentCommandExecutor executor = new IdempotentCommandExecutor(
            new InMemoryProcessedCommandStore()
        );
        KitchenServiceCommandHandlers handlers = new KitchenServiceCommandHandlers(
            ticketRepository,
            eventPublisher,
            executor
        );
        CreateTicketCommand command = command();
        CommandMessage<CreateTicketCommand> message = message(
            "eventuate-command-ticket-101",
            command
        );

        when(ticketRepository.findByOrderId(101L)).thenReturn(Optional.empty());
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> {
            Ticket ticket = invocation.getArgument(0);
            ReflectionTestUtils.setField(ticket, "id", 901L);
            return ticket;
        });

        Message first = handlers.handleCreateTicket(message);
        Message replayed = handlers.handleCreateTicket(message);

        IdempotentCommandContract.assertByteEquivalentReply(first, replayed);
        verify(ticketRepository, times(1)).findByOrderId(101L);
        verify(ticketRepository, times(1)).save(any(Ticket.class));
    }

    @Test
    void unexpectedTicketPersistenceFailureIsNotCachedAsSagaReply() {
        InMemoryProcessedCommandStore store = new InMemoryProcessedCommandStore();
        KitchenServiceCommandHandlers handlers = new KitchenServiceCommandHandlers(
            ticketRepository,
            eventPublisher,
            new IdempotentCommandExecutor(store)
        );
        CreateTicketCommand command = command();
        CommandMessage<CreateTicketCommand> message = message(
            "eventuate-command-ticket-db-failure",
            command
        );

        when(ticketRepository.findByOrderId(101L)).thenReturn(Optional.empty());
        when(ticketRepository.save(any(Ticket.class))).thenThrow(
            new DataAccessResourceFailureException("kitchen database unavailable")
        );

        assertThrows(
            DataAccessResourceFailureException.class,
            () -> handlers.handleCreateTicket(message)
        );
        assertTrue(store.findCompleted(
            "kitchen-service",
            "eventuate-command-ticket-db-failure"
        ).isEmpty());
    }

    private CreateTicketCommand command() {
        return new CreateTicketCommand(
            101L,
            202L,
            List.of(new CreateTicketCommand.TicketLineItemDTO(11L, "Burger", 1))
        );
    }

    private CommandMessage<CreateTicketCommand> message(
        String messageId,
        CreateTicketCommand command
    ) {
        return new CommandMessage<>(
            messageId,
            command,
            Map.of(),
            mock(Message.class)
        );
    }
}
