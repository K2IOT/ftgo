package net.ftgo.kitchen.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.eventuate.tram.commands.common.ReplyMessageHeaders;
import io.eventuate.tram.commands.consumer.CommandMessage;
import io.eventuate.tram.messaging.common.Message;
import net.ftgo.common.orderflow.commands.ApproveTicketCommand;
import net.ftgo.common.orderflow.commands.BeginCancelTicketCommand;
import net.ftgo.common.orderflow.commands.BeginReviseTicketCommand;
import net.ftgo.common.orderflow.commands.CancelTicketCommand;
import net.ftgo.common.orderflow.commands.ConfirmCancelTicketCommand;
import net.ftgo.common.orderflow.commands.ConfirmReviseTicketCommand;
import net.ftgo.common.orderflow.commands.CreateTicketCommand;
import net.ftgo.common.orderflow.commands.UndoCancelTicketCommand;
import net.ftgo.common.orderflow.commands.UndoReviseTicketCommand;
import net.ftgo.common.orderflow.replies.TicketCancellationRefused;
import net.ftgo.common.orderflow.replies.TicketRevisionRefused;
import net.ftgo.kitchen.domain.Ticket;
import net.ftgo.kitchen.domain.TicketLineItem;
import net.ftgo.kitchen.domain.TicketState;
import net.ftgo.kitchen.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KitchenServiceCommandHandlersTest {

    private static final Long RESTAURANT_ID = 1L;
    private static final Long ORDER_ID = 100L;
    private static final Long TICKET_ID = 1000L;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private DomainEventPublisher eventPublisher;

    private KitchenServiceCommandHandlers handlers;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        handlers = new KitchenServiceCommandHandlers(ticketRepository, eventPublisher);
    }

    @Test
    void createsTicketOnceByOrderId() {
        CreateTicketCommand command = createTicketCommand();
        CommandMessage<CreateTicketCommand> message = message(command);
        when(ticketRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.empty());
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Message reply = handlers.handleCreateTicket(message);

        assertNotNull(reply);
        ArgumentCaptor<Ticket> ticket = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketRepository).save(ticket.capture());
        assertEquals(RESTAURANT_ID, ticket.getValue().getRestaurantId());
        assertEquals(ORDER_ID, ticket.getValue().getOrderId());
        assertEquals(TicketState.CREATE_PENDING, ticket.getValue().getState());
    }

    @Test
    void duplicateCreateReturnsEstablishedTicket() {
        Ticket existing = mock(Ticket.class);
        when(existing.getId()).thenReturn(TICKET_ID);
        when(existing.getRestaurantId()).thenReturn(RESTAURANT_ID);
        when(ticketRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(existing));

        Message reply = handlers.handleCreateTicket(message(createTicketCommand()));

        assertNotNull(reply);
        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    @Test
    void approveUsesSagaAcceptanceDeadlineAndRowLock() {
        Ticket ticket = mock(Ticket.class);
        when(ticket.getState()).thenReturn(TicketState.CREATE_PENDING);
        when(ticketRepository.findByIdForUpdate(TICKET_ID)).thenReturn(Optional.of(ticket));
        LocalDateTime deadline = LocalDateTime.of(2026, 7, 23, 12, 0);

        Message reply = handlers.handleApproveTicket(message(
            new ApproveTicketCommand(TICKET_ID, deadline)
        ));

        assertNotNull(reply);
        verify(ticket).approve(deadline);
        verify(ticketRepository).save(ticket);
    }

    @Test
    void duplicateApproveWithSameDeadlineIsNoOp() {
        Ticket ticket = mock(Ticket.class);
        LocalDateTime deadline = LocalDateTime.of(2026, 7, 23, 12, 0);
        when(ticket.getState()).thenReturn(TicketState.AWAITING_ACCEPTANCE);
        when(ticket.getAcceptanceDeadline()).thenReturn(deadline);
        when(ticketRepository.findByIdForUpdate(TICKET_ID)).thenReturn(Optional.of(ticket));

        Message reply = handlers.handleApproveTicket(message(
            new ApproveTicketCommand(TICKET_ID, deadline)
        ));

        assertNotNull(reply);
        verify(ticket, never()).approve(any(LocalDateTime.class));
        verify(ticketRepository, never()).save(ticket);
    }

    @Test
    void cancelAndConfirmCancelPublishExactlyOneEventForStateChange() {
        Ticket ticket = mock(Ticket.class);
        when(ticket.getId()).thenReturn(TICKET_ID);
        when(ticket.getOrderId()).thenReturn(ORDER_ID);
        when(ticket.getState())
            .thenReturn(TicketState.CREATE_PENDING)
            .thenReturn(TicketState.CANCEL_PENDING);
        when(ticketRepository.findByIdForUpdate(TICKET_ID)).thenReturn(Optional.of(ticket));

        handlers.handleCancelTicket(message(new CancelTicketCommand(TICKET_ID)));
        handlers.handleConfirmCancelTicket(message(new ConfirmCancelTicketCommand(TICKET_ID)));

        verify(ticket).cancel();
        verify(ticket).confirmCancel();
        verify(eventPublisher, org.mockito.Mockito.times(2)).publishTicketEvent(
            eq(TICKET_ID),
            any(TicketCancelledEvent.class)
        );
    }

    @Test
    void beginCancelAfterPreparationReturnsTypedFailure() {
        Ticket ticket = preparingTicket();
        when(ticketRepository.findByIdForUpdate(TICKET_ID)).thenReturn(Optional.of(ticket));

        Message reply = handlers.handleBeginCancelTicket(message(
            new BeginCancelTicketCommand(TICKET_ID)
        ));

        assertTypedFailure(reply, TicketCancellationRefused.class);
        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    @Test
    void cancelUndoAndRevisionCommandsUseLockedAggregate() {
        Ticket ticket = mock(Ticket.class);
        when(ticketRepository.findByIdForUpdate(TICKET_ID)).thenReturn(Optional.of(ticket));

        handlers.handleBeginCancelTicket(message(new BeginCancelTicketCommand(TICKET_ID)));
        handlers.handleUndoCancelTicket(message(new UndoCancelTicketCommand(TICKET_ID)));
        handlers.handleBeginReviseTicket(message(new BeginReviseTicketCommand(
            TICKET_ID,
            List.of(new CreateTicketCommand.TicketLineItemDTO(1L, "Burger", 3))
        )));
        handlers.handleConfirmReviseTicket(message(new ConfirmReviseTicketCommand(TICKET_ID)));
        handlers.handleUndoReviseTicket(message(new UndoReviseTicketCommand(TICKET_ID)));

        verify(ticket).beginCancel();
        verify(ticket).undoCancel();
        verify(ticket).beginRevise(any());
        verify(ticket).confirmPendingRevise();
        verify(ticket).undoRevise();
    }

    @Test
    void beginRevisionAfterPreparationReturnsTypedFailure() {
        Ticket ticket = preparingTicket();
        when(ticketRepository.findByIdForUpdate(TICKET_ID)).thenReturn(Optional.of(ticket));

        Message reply = handlers.handleBeginReviseTicket(message(new BeginReviseTicketCommand(
            TICKET_ID,
            List.of(new CreateTicketCommand.TicketLineItemDTO(1L, "Burger", 3))
        )));

        assertTypedFailure(reply, TicketRevisionRefused.class);
        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    private Ticket preparingTicket() {
        Ticket ticket = new Ticket(
            RESTAURANT_ID,
            ORDER_ID,
            List.of(new TicketLineItem(1L, "Burger", 1))
        );
        ticket.approve();
        ticket.accept();
        ticket.preparing();
        return ticket;
    }

    private CreateTicketCommand createTicketCommand() {
        return new CreateTicketCommand(
            ORDER_ID,
            RESTAURANT_ID,
            List.of(new CreateTicketCommand.TicketLineItemDTO(1L, "Burger", 2))
        );
    }

    @SuppressWarnings("unchecked")
    private <T> CommandMessage<T> message(T command) {
        CommandMessage<T> message = mock(CommandMessage.class);
        when(message.getCommand()).thenReturn(command);
        return message;
    }

    private void assertTypedFailure(Message reply, Class<?> type) {
        assertEquals("FAILURE", reply.getRequiredHeader(ReplyMessageHeaders.REPLY_OUTCOME));
        assertEquals(type.getName(), reply.getRequiredHeader(ReplyMessageHeaders.REPLY_TYPE));
        try {
            objectMapper.readValue(reply.getPayload(), type);
        } catch (Exception e) {
            fail(e);
        }
    }
}
