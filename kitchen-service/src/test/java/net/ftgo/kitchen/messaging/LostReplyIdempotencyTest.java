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
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

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
        CreateTicketCommand command = new CreateTicketCommand(
            101L,
            202L,
            List.of(new CreateTicketCommand.TicketLineItemDTO(11L, "Burger", 1))
        );
        CommandMessage<CreateTicketCommand> message = new CommandMessage<>(
            "eventuate-command-ticket-101",
            command,
            Map.of(),
            mock(Message.class)
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
}
