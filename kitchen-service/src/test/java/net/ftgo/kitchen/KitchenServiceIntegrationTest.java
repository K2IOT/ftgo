package net.ftgo.kitchen;

import io.eventuate.tram.commands.consumer.CommandMessage;
import io.eventuate.tram.messaging.common.Message;
import net.ftgo.kitchen.domain.Ticket;
import net.ftgo.kitchen.domain.TicketState;
import net.ftgo.kitchen.messaging.*;
import net.ftgo.kitchen.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration tests for Kitchen Service with real database and Kafka.
 * 
 * Tests:
 * - End-to-end command handling with database persistence
 * - Saga participation workflows
 * - Transaction management
 * - Event publishing
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@DisplayName("Kitchen Service Integration Tests")
class KitchenServiceIntegrationTest {
    
    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
            .withDatabaseName("kitchen_service")
            .withUsername("test")
            .withPassword("test");
    
    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }
    
    @Autowired
    private TicketRepository ticketRepository;
    
    @Autowired
    private KitchenServiceCommandHandlers commandHandlers;
    
    private static final Long RESTAURANT_ID = 1L;
    private static final Long ORDER_ID = 100L;
    
    @BeforeEach
    void setUp() {
        ticketRepository.deleteAll();
    }
    
    private List<CreateTicketCommand.TicketLineItemDTO> createSampleLineItemDTOs() {
        return Arrays.asList(
            new CreateTicketCommand.TicketLineItemDTO(1L, "Burger", 2),
            new CreateTicketCommand.TicketLineItemDTO(2L, "Fries", 1)
        );
    }
    
    @Test
    @DisplayName("Should create ticket and persist to database")
    @Transactional
    void shouldCreateTicketAndPersistToDatabase() {
        // Given
        CreateTicketCommand command = new CreateTicketCommand(
            ORDER_ID, RESTAURANT_ID, createSampleLineItemDTOs()
        );
        CommandMessage<CreateTicketCommand> cm = mock(CommandMessage.class);
        when(cm.getCommand()).thenReturn(command);
        
        // When
        Message reply = commandHandlers.handleCreateTicket(cm);
        
        // Then
        assertNotNull(reply);
        
        // Verify ticket was persisted
        Optional<Ticket> savedTicket = ticketRepository.findByOrderId(ORDER_ID);
        assertTrue(savedTicket.isPresent());
        assertEquals(TicketState.CREATE_PENDING, savedTicket.get().getState());
        assertEquals(RESTAURANT_ID, savedTicket.get().getRestaurantId());
        assertEquals(2, savedTicket.get().getLineItems().size());
    }
    
    @Test
    @DisplayName("Should complete CreateOrderSaga workflow")
    @Transactional
    void shouldCompleteCreateOrderSagaWorkflow() {
        // Step 1: Create ticket
        CreateTicketCommand createCommand = new CreateTicketCommand(
            ORDER_ID, RESTAURANT_ID, createSampleLineItemDTOs()
        );
        CommandMessage<CreateTicketCommand> createCm = mock(CommandMessage.class);
        when(createCm.getCommand()).thenReturn(createCommand);
        
        commandHandlers.handleCreateTicket(createCm);
        
        Ticket ticket = ticketRepository.findByOrderId(ORDER_ID).orElseThrow();
        assertEquals(TicketState.CREATE_PENDING, ticket.getState());
        
        // Step 2: Approve ticket
        ApproveTicketCommand approveCommand = new ApproveTicketCommand(ticket.getId());
        CommandMessage<ApproveTicketCommand> approveCm = mock(CommandMessage.class);
        when(approveCm.getCommand()).thenReturn(approveCommand);
        
        commandHandlers.handleApproveTicket(approveCm);
        
        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertEquals(TicketState.AWAITING_ACCEPTANCE, ticket.getState());
    }
    
    @Test
    @DisplayName("Should handle CreateOrderSaga compensation")
    @Transactional
    void shouldHandleCreateOrderSagaCompensation() {
        // Step 1: Create ticket
        CreateTicketCommand createCommand = new CreateTicketCommand(
            ORDER_ID, RESTAURANT_ID, createSampleLineItemDTOs()
        );
        CommandMessage<CreateTicketCommand> createCm = mock(CommandMessage.class);
        when(createCm.getCommand()).thenReturn(createCommand);
        
        commandHandlers.handleCreateTicket(createCm);
        
        Ticket ticket = ticketRepository.findByOrderId(ORDER_ID).orElseThrow();
        Long ticketId = ticket.getId();
        
        // Step 2: Cancel ticket (compensation)
        CancelTicketCommand cancelCommand = new CancelTicketCommand(ticketId);
        CommandMessage<CancelTicketCommand> cancelCm = mock(CommandMessage.class);
        when(cancelCm.getCommand()).thenReturn(cancelCommand);
        
        commandHandlers.handleCancelTicket(cancelCm);
        
        ticket = ticketRepository.findById(ticketId).orElseThrow();
        assertEquals(TicketState.CANCELLED, ticket.getState());
    }
    
    @Test
    @DisplayName("Should complete CancelOrderSaga workflow")
    @Transactional
    void shouldCompleteCancelOrderSagaWorkflow() {
        // Setup: Create and approve ticket
        CreateTicketCommand createCommand = new CreateTicketCommand(
            ORDER_ID, RESTAURANT_ID, createSampleLineItemDTOs()
        );
        CommandMessage<CreateTicketCommand> createCm = mock(CommandMessage.class);
        when(createCm.getCommand()).thenReturn(createCommand);
        commandHandlers.handleCreateTicket(createCm);
        
        Ticket ticket = ticketRepository.findByOrderId(ORDER_ID).orElseThrow();
        Long ticketId = ticket.getId();
        
        ApproveTicketCommand approveCommand = new ApproveTicketCommand(ticketId);
        CommandMessage<ApproveTicketCommand> approveCm = mock(CommandMessage.class);
        when(approveCm.getCommand()).thenReturn(approveCommand);
        commandHandlers.handleApproveTicket(approveCm);
        
        // Step 1: Begin cancel
        BeginCancelTicketCommand beginCancelCommand = new BeginCancelTicketCommand(ticketId);
        CommandMessage<BeginCancelTicketCommand> beginCancelCm = mock(CommandMessage.class);
        when(beginCancelCm.getCommand()).thenReturn(beginCancelCommand);
        
        commandHandlers.handleBeginCancelTicket(beginCancelCm);
        
        // Step 2: Confirm cancel
        ConfirmCancelTicketCommand confirmCancelCommand = new ConfirmCancelTicketCommand(ticketId);
        CommandMessage<ConfirmCancelTicketCommand> confirmCancelCm = mock(CommandMessage.class);
        when(confirmCancelCm.getCommand()).thenReturn(confirmCancelCommand);
        
        commandHandlers.handleConfirmCancelTicket(confirmCancelCm);
        
        ticket = ticketRepository.findById(ticketId).orElseThrow();
        assertEquals(TicketState.CANCELLED, ticket.getState());
    }
    
    @Test
    @DisplayName("Should handle CancelOrderSaga compensation")
    @Transactional
    void shouldHandleCancelOrderSagaCompensation() {
        // Setup: Create and approve ticket
        CreateTicketCommand createCommand = new CreateTicketCommand(
            ORDER_ID, RESTAURANT_ID, createSampleLineItemDTOs()
        );
        CommandMessage<CreateTicketCommand> createCm = mock(CommandMessage.class);
        when(createCm.getCommand()).thenReturn(createCommand);
        commandHandlers.handleCreateTicket(createCm);
        
        Ticket ticket = ticketRepository.findByOrderId(ORDER_ID).orElseThrow();
        Long ticketId = ticket.getId();
        
        ApproveTicketCommand approveCommand = new ApproveTicketCommand(ticketId);
        CommandMessage<ApproveTicketCommand> approveCm = mock(CommandMessage.class);
        when(approveCm.getCommand()).thenReturn(approveCommand);
        commandHandlers.handleApproveTicket(approveCm);
        
        // Step 1: Begin cancel
        BeginCancelTicketCommand beginCancelCommand = new BeginCancelTicketCommand(ticketId);
        CommandMessage<BeginCancelTicketCommand> beginCancelCm = mock(CommandMessage.class);
        when(beginCancelCm.getCommand()).thenReturn(beginCancelCommand);
        commandHandlers.handleBeginCancelTicket(beginCancelCm);
        
        // Step 2: Undo cancel (compensation)
        UndoCancelTicketCommand undoCancelCommand = new UndoCancelTicketCommand(ticketId);
        CommandMessage<UndoCancelTicketCommand> undoCancelCm = mock(CommandMessage.class);
        when(undoCancelCm.getCommand()).thenReturn(undoCancelCommand);
        
        commandHandlers.handleUndoCancelTicket(undoCancelCm);
        
        ticket = ticketRepository.findById(ticketId).orElseThrow();
        assertEquals(TicketState.AWAITING_ACCEPTANCE, ticket.getState());
    }
    
    @Test
    @DisplayName("Should complete ReviseOrderSaga workflow")
    @Transactional
    void shouldCompleteReviseOrderSagaWorkflow() {
        // Setup: Create and approve ticket
        CreateTicketCommand createCommand = new CreateTicketCommand(
            ORDER_ID, RESTAURANT_ID, createSampleLineItemDTOs()
        );
        CommandMessage<CreateTicketCommand> createCm = mock(CommandMessage.class);
        when(createCm.getCommand()).thenReturn(createCommand);
        commandHandlers.handleCreateTicket(createCm);
        
        Ticket ticket = ticketRepository.findByOrderId(ORDER_ID).orElseThrow();
        Long ticketId = ticket.getId();
        
        ApproveTicketCommand approveCommand = new ApproveTicketCommand(ticketId);
        CommandMessage<ApproveTicketCommand> approveCm = mock(CommandMessage.class);
        when(approveCm.getCommand()).thenReturn(approveCommand);
        commandHandlers.handleApproveTicket(approveCm);
        
        // Step 1: Begin revise
        List<CreateTicketCommand.TicketLineItemDTO> revisedItems = Arrays.asList(
            new CreateTicketCommand.TicketLineItemDTO(1L, "Burger", 3),
            new CreateTicketCommand.TicketLineItemDTO(3L, "Salad", 1)
        );
        BeginReviseTicketCommand beginReviseCommand = new BeginReviseTicketCommand(ticketId, revisedItems);
        CommandMessage<BeginReviseTicketCommand> beginReviseCm = mock(CommandMessage.class);
        when(beginReviseCm.getCommand()).thenReturn(beginReviseCommand);
        
        commandHandlers.handleBeginReviseTicket(beginReviseCm);
        
        // Step 2: Confirm revise
        ConfirmReviseTicketCommand confirmReviseCommand = new ConfirmReviseTicketCommand(ticketId, revisedItems);
        CommandMessage<ConfirmReviseTicketCommand> confirmReviseCm = mock(CommandMessage.class);
        when(confirmReviseCm.getCommand()).thenReturn(confirmReviseCommand);
        
        commandHandlers.handleConfirmReviseTicket(confirmReviseCm);
        
        ticket = ticketRepository.findById(ticketId).orElseThrow();
        assertEquals(2, ticket.getLineItems().size());
        assertEquals(3, ticket.getLineItems().get(0).getQuantity());
        assertEquals("Salad", ticket.getLineItems().get(1).getName());
    }
    
    @Test
    @DisplayName("Should handle ReviseOrderSaga compensation")
    @Transactional
    void shouldHandleReviseOrderSagaCompensation() {
        // Setup: Create and approve ticket
        List<CreateTicketCommand.TicketLineItemDTO> originalItems = createSampleLineItemDTOs();
        CreateTicketCommand createCommand = new CreateTicketCommand(
            ORDER_ID, RESTAURANT_ID, originalItems
        );
        CommandMessage<CreateTicketCommand> createCm = mock(CommandMessage.class);
        when(createCm.getCommand()).thenReturn(createCommand);
        commandHandlers.handleCreateTicket(createCm);
        
        Ticket ticket = ticketRepository.findByOrderId(ORDER_ID).orElseThrow();
        Long ticketId = ticket.getId();
        
        ApproveTicketCommand approveCommand = new ApproveTicketCommand(ticketId);
        CommandMessage<ApproveTicketCommand> approveCm = mock(CommandMessage.class);
        when(approveCm.getCommand()).thenReturn(approveCommand);
        commandHandlers.handleApproveTicket(approveCm);
        
        // Step 1: Begin revise
        List<CreateTicketCommand.TicketLineItemDTO> revisedItems = Arrays.asList(
            new CreateTicketCommand.TicketLineItemDTO(1L, "Burger", 3)
        );
        BeginReviseTicketCommand beginReviseCommand = new BeginReviseTicketCommand(ticketId, revisedItems);
        CommandMessage<BeginReviseTicketCommand> beginReviseCm = mock(CommandMessage.class);
        when(beginReviseCm.getCommand()).thenReturn(beginReviseCommand);
        commandHandlers.handleBeginReviseTicket(beginReviseCm);
        
        // Step 2: Undo revise (compensation) - restores state from ticket's previousState
        UndoReviseTicketCommand undoReviseCommand = new UndoReviseTicketCommand(ticketId);
        CommandMessage<UndoReviseTicketCommand> undoReviseCm = mock(CommandMessage.class);
        when(undoReviseCm.getCommand()).thenReturn(undoReviseCommand);
        
        commandHandlers.handleUndoReviseTicket(undoReviseCm);
        
        ticket = ticketRepository.findById(ticketId).orElseThrow();
        // undoRevise restores state to previousState (AWAITING_ACCEPTANCE) without changing line items
        assertEquals(TicketState.AWAITING_ACCEPTANCE, ticket.getState());
        assertEquals(2, ticket.getLineItems().size());
    }
    
    @Test
    @DisplayName("Should verify ticket line items match order line items after persistence")
    @Transactional
    void shouldVerifyTicketLineItemsMatchOrderLineItemsAfterPersistence() {
        // Given
        List<CreateTicketCommand.TicketLineItemDTO> orderLineItems = Arrays.asList(
            new CreateTicketCommand.TicketLineItemDTO(1L, "Burger", 2),
            new CreateTicketCommand.TicketLineItemDTO(2L, "Fries", 1),
            new CreateTicketCommand.TicketLineItemDTO(3L, "Salad", 1)
        );
        
        CreateTicketCommand command = new CreateTicketCommand(
            ORDER_ID, RESTAURANT_ID, orderLineItems
        );
        CommandMessage<CreateTicketCommand> cm = mock(CommandMessage.class);
        when(cm.getCommand()).thenReturn(command);
        
        // When
        commandHandlers.handleCreateTicket(cm);
        
        // Then
        Ticket ticket = ticketRepository.findByOrderId(ORDER_ID).orElseThrow();
        assertEquals(orderLineItems.size(), ticket.getLineItems().size());
        
        for (int i = 0; i < orderLineItems.size(); i++) {
            CreateTicketCommand.TicketLineItemDTO orderItem = orderLineItems.get(i);
            var ticketItem = ticket.getLineItems().get(i);
            
            assertEquals(orderItem.getMenuItemId(), ticketItem.getMenuItemId(),
                "Menu item ID should match for item " + i);
            assertEquals(orderItem.getName(), ticketItem.getName(),
                "Name should match for item " + i);
            assertEquals(orderItem.getQuantity(), ticketItem.getQuantity(),
                "Quantity should match for item " + i);
        }
    }
    
    @Test
    @DisplayName("Should query tickets by restaurant and state")
    @Transactional
    void shouldQueryTicketsByRestaurantAndState() {
        // Given - Create multiple tickets
        for (int i = 0; i < 3; i++) {
            CreateTicketCommand command = new CreateTicketCommand(
                ORDER_ID + i, RESTAURANT_ID, createSampleLineItemDTOs()
            );
            CommandMessage<CreateTicketCommand> cm = mock(CommandMessage.class);
            when(cm.getCommand()).thenReturn(command);
            commandHandlers.handleCreateTicket(cm);
        }
        
        // Approve one ticket
        Ticket ticket = ticketRepository.findByOrderId(ORDER_ID).orElseThrow();
        ApproveTicketCommand approveCommand = new ApproveTicketCommand(ticket.getId());
        CommandMessage<ApproveTicketCommand> approveCm = mock(CommandMessage.class);
        when(approveCm.getCommand()).thenReturn(approveCommand);
        commandHandlers.handleApproveTicket(approveCm);
        
        // When
        List<Ticket> pendingTickets = ticketRepository.findByRestaurantIdAndState(
            RESTAURANT_ID, TicketState.CREATE_PENDING
        );
        List<Ticket> awaitingTickets = ticketRepository.findByRestaurantIdAndState(
            RESTAURANT_ID, TicketState.AWAITING_ACCEPTANCE
        );
        
        // Then
        assertEquals(2, pendingTickets.size());
        assertEquals(1, awaitingTickets.size());
    }
}
