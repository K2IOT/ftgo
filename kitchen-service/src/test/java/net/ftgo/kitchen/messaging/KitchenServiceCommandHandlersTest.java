package net.ftgo.kitchen.messaging;

import io.eventuate.tram.commands.consumer.CommandMessage;
import io.eventuate.tram.commands.common.ReplyMessageHeaders;
import io.eventuate.tram.messaging.common.Message;
import net.ftgo.common.orderflow.commands.ApproveTicketCommand;
import net.ftgo.common.orderflow.commands.BeginCancelTicketCommand;
import net.ftgo.common.orderflow.commands.CancelTicketCommand;
import net.ftgo.common.orderflow.commands.ConfirmCancelTicketCommand;
import net.ftgo.common.orderflow.commands.ConfirmReviseTicketCommand;
import net.ftgo.common.orderflow.commands.CreateTicketCommand;
import net.ftgo.common.orderflow.commands.UndoCancelTicketCommand;
import net.ftgo.common.orderflow.commands.UndoReviseTicketCommand;
import net.ftgo.common.orderflow.commands.BeginReviseTicketCommand;
import net.ftgo.common.orderflow.replies.TicketCancellationRefused;
import net.ftgo.common.orderflow.replies.TicketRevisionRefused;
import net.ftgo.kitchen.domain.Ticket;
import net.ftgo.kitchen.domain.TicketLineItem;
import net.ftgo.kitchen.domain.TicketState;
import net.ftgo.kitchen.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for KitchenServiceCommandHandlers.
 * 
 * Tests all command handlers for saga participation:
 * - CreateTicketCommand
 * - ApproveTicketCommand
 * - CancelTicketCommand
 * - BeginCancelTicketCommand
 * - ConfirmCancelTicketCommand
 * - UndoCancelTicketCommand
 * - BeginReviseTicketCommand
 * - ConfirmReviseTicketCommand
 * - UndoReviseTicketCommand
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Kitchen Service Command Handlers Tests")
class KitchenServiceCommandHandlersTest {
    
    @Mock
    private TicketRepository ticketRepository;
    
    @Mock
    private DomainEventPublisher eventPublisher;
    
    private KitchenServiceCommandHandlers commandHandlers;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
    
    private static final Long RESTAURANT_ID = 1L;
    private static final Long ORDER_ID = 100L;
    private static final Long TICKET_ID = 1000L;
    
    @BeforeEach
    void setUp() {
        commandHandlers = new KitchenServiceCommandHandlers(ticketRepository, eventPublisher);
    }
    
    private List<CreateTicketCommand.TicketLineItemDTO> createSampleLineItemDTOs() {
        return Arrays.asList(
            new CreateTicketCommand.TicketLineItemDTO(1L, "Burger", 2),
            new CreateTicketCommand.TicketLineItemDTO(2L, "Fries", 1)
        );
    }

    private List<TicketLineItem> createSampleTicketLineItems() {
        return Arrays.asList(
            new TicketLineItem(1L, "Burger", 2),
            new TicketLineItem(2L, "Fries", 1)
        );
    }
    
    @Test
    @DisplayName("Should handle CreateTicketCommand successfully")
    void shouldHandleCreateTicketCommandSuccessfully() {
        // Given
        CreateTicketCommand command = new CreateTicketCommand(
            ORDER_ID, RESTAURANT_ID, createSampleLineItemDTOs()
        );
        CommandMessage<CreateTicketCommand> cm = mock(CommandMessage.class);
        when(cm.getCommand()).thenReturn(command);
        
        ArgumentCaptor<Ticket> ticketCaptor = ArgumentCaptor.forClass(Ticket.class);
        when(ticketRepository.save(ticketCaptor.capture())).thenAnswer(invocation -> {
            Ticket ticket = invocation.getArgument(0);
            // Simulate ID assignment by JPA
            return ticket;
        });
        
        // When
        Message reply = commandHandlers.handleCreateTicket(cm);
        
        // Then
        assertNotNull(reply);
        verify(ticketRepository).save(any(Ticket.class));
        
        Ticket savedTicket = ticketCaptor.getValue();
        assertEquals(RESTAURANT_ID, savedTicket.getRestaurantId());
        assertEquals(ORDER_ID, savedTicket.getOrderId());
        assertEquals(TicketState.CREATE_PENDING, savedTicket.getState());
        assertEquals(2, savedTicket.getLineItems().size());
    }
    
    @Test
    @DisplayName("Should handle CreateTicketCommand with invalid data")
    void shouldHandleCreateTicketCommandWithInvalidData() {
        // Given - null order ID
        CreateTicketCommand command = new CreateTicketCommand(
            null, RESTAURANT_ID, createSampleLineItemDTOs()
        );
        CommandMessage<CreateTicketCommand> cm = mock(CommandMessage.class);
        when(cm.getCommand()).thenReturn(command);
        
        // When
        Message reply = commandHandlers.handleCreateTicket(cm);
        
        // Then
        assertNotNull(reply);
        verify(ticketRepository, never()).save(any(Ticket.class));
    }
    
    @Test
    @DisplayName("Should handle ApproveTicketCommand successfully")
    void shouldHandleApproveTicketCommandSuccessfully() {
        // Given
        Ticket ticket = mock(Ticket.class);
        when(ticketRepository.findById(TICKET_ID)).thenReturn(Optional.of(ticket));
        
        ApproveTicketCommand command = new ApproveTicketCommand(TICKET_ID);
        CommandMessage<ApproveTicketCommand> cm = mock(CommandMessage.class);
        when(cm.getCommand()).thenReturn(command);
        
        // When
        Message reply = commandHandlers.handleApproveTicket(cm);
        
        // Then
        assertNotNull(reply);
        verify(ticket).approve();
        verify(ticketRepository).save(ticket);
    }
    
    @Test
    @DisplayName("Should handle ApproveTicketCommand when ticket not found")
    void shouldHandleApproveTicketCommandWhenTicketNotFound() {
        // Given
        when(ticketRepository.findById(TICKET_ID)).thenReturn(Optional.empty());
        
        ApproveTicketCommand command = new ApproveTicketCommand(TICKET_ID);
        CommandMessage<ApproveTicketCommand> cm = mock(CommandMessage.class);
        when(cm.getCommand()).thenReturn(command);
        
        // When
        Message reply = commandHandlers.handleApproveTicket(cm);
        
        // Then
        assertNotNull(reply);
        verify(ticketRepository, never()).save(any(Ticket.class));
    }
    
    @Test
    @DisplayName("Should handle ApproveTicketCommand when ticket in wrong state")
    void shouldHandleApproveTicketCommandWhenTicketInWrongState() {
        // Given
        Ticket ticket = mock(Ticket.class);
        when(ticketRepository.findById(TICKET_ID)).thenReturn(Optional.of(ticket));
        doThrow(new IllegalStateException("Cannot approve ticket in state AWAITING_ACCEPTANCE"))
            .when(ticket).approve();
        
        ApproveTicketCommand command = new ApproveTicketCommand(TICKET_ID);
        CommandMessage<ApproveTicketCommand> cm = mock(CommandMessage.class);
        when(cm.getCommand()).thenReturn(command);
        
        // When
        Message reply = commandHandlers.handleApproveTicket(cm);
        
        // Then
        assertNotNull(reply);
        verify(ticket).approve();
        verify(ticketRepository, never()).save(any(Ticket.class));
    }
    
    @Test
    @DisplayName("Should handle CancelTicketCommand successfully")
    void shouldHandleCancelTicketCommandSuccessfully() {
        // Given
        Ticket ticket = mock(Ticket.class);
        when(ticket.getId()).thenReturn(TICKET_ID);
        when(ticket.getOrderId()).thenReturn(ORDER_ID);
        when(ticketRepository.findById(TICKET_ID)).thenReturn(Optional.of(ticket));
        
        CancelTicketCommand command = new CancelTicketCommand(TICKET_ID);
        CommandMessage<CancelTicketCommand> cm = mock(CommandMessage.class);
        when(cm.getCommand()).thenReturn(command);
        
        // When
        Message reply = commandHandlers.handleCancelTicket(cm);
        
        // Then
        assertNotNull(reply);
        verify(ticket).cancel();
        verify(ticketRepository).save(ticket);
        verify(eventPublisher).publishTicketEvent(eq(TICKET_ID), any(TicketCancelledEvent.class));
    }
    
    @Test
    @DisplayName("Should handle BeginCancelTicketCommand successfully")
    void shouldHandleBeginCancelTicketCommandSuccessfully() {
        // Given
        Ticket ticket = mock(Ticket.class);
        when(ticketRepository.findById(TICKET_ID)).thenReturn(Optional.of(ticket));
        
        BeginCancelTicketCommand command = new BeginCancelTicketCommand(TICKET_ID);
        CommandMessage<BeginCancelTicketCommand> cm = mock(CommandMessage.class);
        when(cm.getCommand()).thenReturn(command);
        
        // When
        Message reply = commandHandlers.handleBeginCancelTicket(cm);
        
        // Then
        assertNotNull(reply);
        verify(ticket).beginCancel();
        verify(ticketRepository).save(ticket);
    }

    @Test
    @DisplayName("Should reject BeginCancelTicketCommand after preparation has begun")
    void shouldRejectBeginCancelTicketCommandAfterPreparationHasBegun() {
        // Given
        Ticket ticket = new Ticket(RESTAURANT_ID, ORDER_ID, createSampleTicketLineItems());
        ticket.approve();
        ticket.accept();
        ticket.preparing();
        when(ticketRepository.findById(TICKET_ID)).thenReturn(Optional.of(ticket));

        BeginCancelTicketCommand command = new BeginCancelTicketCommand(TICKET_ID);
        CommandMessage<BeginCancelTicketCommand> cm = mock(CommandMessage.class);
        when(cm.getCommand()).thenReturn(command);

        // When
        Message reply = commandHandlers.handleBeginCancelTicket(cm);

        // Then
        assertNotNull(reply);
        assertEquals("FAILURE", reply.getRequiredHeader(ReplyMessageHeaders.REPLY_OUTCOME));
        assertEquals(TicketCancellationRefused.class.getName(), reply.getRequiredHeader(ReplyMessageHeaders.REPLY_TYPE));
        try {
            TicketCancellationRefused refusal = objectMapper.readValue(reply.getPayload(), TicketCancellationRefused.class);
            assertEquals(TicketCancellationRefused.PREPARATION_ALREADY_STARTED, refusal.getReasonCode());
        } catch (Exception e) {
            fail(e);
        }
        assertEquals(TicketState.PREPARING, ticket.getState());
        verify(ticketRepository, never()).save(any(Ticket.class));
    }
    
    @Test
    @DisplayName("Should handle ConfirmCancelTicketCommand successfully")
    void shouldHandleConfirmCancelTicketCommandSuccessfully() {
        // Given
        Ticket ticket = mock(Ticket.class);
        when(ticket.getId()).thenReturn(TICKET_ID);
        when(ticket.getOrderId()).thenReturn(ORDER_ID);
        when(ticketRepository.findById(TICKET_ID)).thenReturn(Optional.of(ticket));
        
        ConfirmCancelTicketCommand command = new ConfirmCancelTicketCommand(TICKET_ID);
        CommandMessage<ConfirmCancelTicketCommand> cm = mock(CommandMessage.class);
        when(cm.getCommand()).thenReturn(command);
        
        // When
        Message reply = commandHandlers.handleConfirmCancelTicket(cm);
        
        // Then
        assertNotNull(reply);
        verify(ticket).confirmCancel();
        verify(ticketRepository).save(ticket);
        verify(eventPublisher).publishTicketEvent(eq(TICKET_ID), any(TicketCancelledEvent.class));
    }
    
    @Test
    @DisplayName("Should handle UndoCancelTicketCommand successfully")
    void shouldHandleUndoCancelTicketCommandSuccessfully() {
        // Given
        Ticket ticket = mock(Ticket.class);
        when(ticketRepository.findById(TICKET_ID)).thenReturn(Optional.of(ticket));
        
        UndoCancelTicketCommand command = new UndoCancelTicketCommand(TICKET_ID);
        CommandMessage<UndoCancelTicketCommand> cm = mock(CommandMessage.class);
        when(cm.getCommand()).thenReturn(command);
        
        // When
        Message reply = commandHandlers.handleUndoCancelTicket(cm);
        
        // Then
        assertNotNull(reply);
        verify(ticket).undoCancel();
        verify(ticketRepository).save(ticket);
    }
    
    @Test
    @DisplayName("Should handle BeginReviseTicketCommand successfully")
    void shouldHandleBeginReviseTicketCommandSuccessfully() {
        // Given
        Ticket ticket = mock(Ticket.class);
        when(ticketRepository.findById(TICKET_ID)).thenReturn(Optional.of(ticket));
        
        List<CreateTicketCommand.TicketLineItemDTO> revisedItems = Arrays.asList(
            new CreateTicketCommand.TicketLineItemDTO(1L, "Burger", 3)
        );
        BeginReviseTicketCommand command = new BeginReviseTicketCommand(TICKET_ID, revisedItems);
        CommandMessage<BeginReviseTicketCommand> cm = mock(CommandMessage.class);
        when(cm.getCommand()).thenReturn(command);
        
        // When
        Message reply = commandHandlers.handleBeginReviseTicket(cm);
        
        // Then
        assertNotNull(reply);
        verify(ticket).beginRevise(any());
        verify(ticketRepository).save(ticket);
    }

    @Test
    @DisplayName("Should reject BeginReviseTicketCommand after preparation has begun")
    void shouldRejectBeginReviseTicketCommandAfterPreparationHasBegun() {
        // Given
        Ticket ticket = new Ticket(RESTAURANT_ID, ORDER_ID, createSampleTicketLineItems());
        ticket.approve();
        ticket.accept();
        ticket.preparing();
        when(ticketRepository.findById(TICKET_ID)).thenReturn(Optional.of(ticket));

        List<CreateTicketCommand.TicketLineItemDTO> revisedItems = Arrays.asList(
            new CreateTicketCommand.TicketLineItemDTO(1L, "Burger", 3)
        );
        BeginReviseTicketCommand command = new BeginReviseTicketCommand(TICKET_ID, revisedItems);
        CommandMessage<BeginReviseTicketCommand> cm = mock(CommandMessage.class);
        when(cm.getCommand()).thenReturn(command);

        // When
        Message reply = commandHandlers.handleBeginReviseTicket(cm);

        // Then
        assertNotNull(reply);
        assertEquals("FAILURE", reply.getRequiredHeader(ReplyMessageHeaders.REPLY_OUTCOME));
        assertEquals(TicketRevisionRefused.class.getName(), reply.getRequiredHeader(ReplyMessageHeaders.REPLY_TYPE));
        try {
            TicketRevisionRefused refusal = objectMapper.readValue(reply.getPayload(), TicketRevisionRefused.class);
            assertEquals(TicketRevisionRefused.PREPARATION_ALREADY_STARTED, refusal.getReasonCode());
        } catch (Exception e) {
            fail(e);
        }
        assertEquals(TicketState.PREPARING, ticket.getState());
        verify(ticketRepository, never()).save(any(Ticket.class));
    }
    
    @Test
    @DisplayName("Should handle ConfirmReviseTicketCommand successfully")
    void shouldHandleConfirmReviseTicketCommandSuccessfully() {
        // Given
        Ticket ticket = mock(Ticket.class);
        when(ticketRepository.findById(TICKET_ID)).thenReturn(Optional.of(ticket));
        
        ConfirmReviseTicketCommand command = new ConfirmReviseTicketCommand(TICKET_ID);
        CommandMessage<ConfirmReviseTicketCommand> cm = mock(CommandMessage.class);
        when(cm.getCommand()).thenReturn(command);
        
        // When
        Message reply = commandHandlers.handleConfirmReviseTicket(cm);
        
        // Then
        assertNotNull(reply);
        verify(ticket).confirmPendingRevise();
        verify(ticketRepository).save(ticket);
    }
    
    @Test
    @DisplayName("Should handle UndoReviseTicketCommand successfully")
    void shouldHandleUndoReviseTicketCommandSuccessfully() {
        // Given
        Ticket ticket = mock(Ticket.class);
        when(ticketRepository.findById(TICKET_ID)).thenReturn(Optional.of(ticket));
        
        UndoReviseTicketCommand command = new UndoReviseTicketCommand(TICKET_ID);
        CommandMessage<UndoReviseTicketCommand> cm = mock(CommandMessage.class);
        when(cm.getCommand()).thenReturn(command);
        
        // When
        Message reply = commandHandlers.handleUndoReviseTicket(cm);
        
        // Then
        assertNotNull(reply);
        verify(ticket).undoRevise();
        verify(ticketRepository).save(ticket);
    }
    
    @Test
    @DisplayName("Should verify ticket line items match order line items")
    void shouldVerifyTicketLineItemsMatchOrderLineItems() {
        // Given
        List<CreateTicketCommand.TicketLineItemDTO> orderLineItems = Arrays.asList(
            new CreateTicketCommand.TicketLineItemDTO(1L, "Burger", 2),
            new CreateTicketCommand.TicketLineItemDTO(2L, "Fries", 1)
        );
        
        CreateTicketCommand command = new CreateTicketCommand(
            ORDER_ID, RESTAURANT_ID, orderLineItems
        );
        CommandMessage<CreateTicketCommand> cm = mock(CommandMessage.class);
        when(cm.getCommand()).thenReturn(command);
        
        ArgumentCaptor<Ticket> ticketCaptor = ArgumentCaptor.forClass(Ticket.class);
        when(ticketRepository.save(ticketCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));
        
        // When
        commandHandlers.handleCreateTicket(cm);
        
        // Then
        Ticket savedTicket = ticketCaptor.getValue();
        List<net.ftgo.kitchen.domain.TicketLineItem> ticketLineItems = savedTicket.getLineItems();
        
        assertEquals(orderLineItems.size(), ticketLineItems.size());
        
        for (int i = 0; i < orderLineItems.size(); i++) {
            CreateTicketCommand.TicketLineItemDTO orderItem = orderLineItems.get(i);
            net.ftgo.kitchen.domain.TicketLineItem ticketItem = ticketLineItems.get(i);
            
            assertEquals(orderItem.getMenuItemId(), ticketItem.getMenuItemId(),
                "Menu item ID should match");
            assertEquals(orderItem.getName(), ticketItem.getName(),
                "Name should match");
            assertEquals(orderItem.getQuantity(), ticketItem.getQuantity(),
                "Quantity should match");
        }
    }
}
