package net.ftgo.kitchen;

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
import net.ftgo.kitchen.domain.Ticket;
import net.ftgo.kitchen.domain.TicketState;
import net.ftgo.kitchen.messaging.KitchenServiceCommandHandlers;
import net.ftgo.kitchen.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
        Message reply = commandHandlers.handleCreateTicket(message(
            new CreateTicketCommand(ORDER_ID, RESTAURANT_ID, createSampleLineItemDTOs()),
            "create-persist-1"
        ));

        assertNotNull(reply);
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
        commandHandlers.handleCreateTicket(message(
            new CreateTicketCommand(ORDER_ID, RESTAURANT_ID, createSampleLineItemDTOs()),
            "create-saga-create"
        ));

        Ticket ticket = ticketRepository.findByOrderId(ORDER_ID).orElseThrow();
        assertEquals(TicketState.CREATE_PENDING, ticket.getState());

        commandHandlers.handleApproveTicket(message(
            new ApproveTicketCommand(ticket.getId()),
            "create-saga-approve"
        ));

        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertEquals(TicketState.AWAITING_ACCEPTANCE, ticket.getState());
    }

    @Test
    @DisplayName("Should handle CreateOrderSaga compensation")
    @Transactional
    void shouldHandleCreateOrderSagaCompensation() {
        commandHandlers.handleCreateTicket(message(
            new CreateTicketCommand(ORDER_ID, RESTAURANT_ID, createSampleLineItemDTOs()),
            "create-comp-create"
        ));

        Ticket ticket = ticketRepository.findByOrderId(ORDER_ID).orElseThrow();
        commandHandlers.handleCancelTicket(message(
            new CancelTicketCommand(ticket.getId()),
            "create-comp-cancel"
        ));

        assertEquals(
            TicketState.CANCELLED,
            ticketRepository.findById(ticket.getId()).orElseThrow().getState()
        );
    }

    @Test
    @DisplayName("Should complete CancelOrderSaga workflow")
    @Transactional
    void shouldCompleteCancelOrderSagaWorkflow() {
        Ticket ticket = createAndApproveTicket("cancel-saga");

        commandHandlers.handleBeginCancelTicket(message(
            new BeginCancelTicketCommand(ticket.getId()),
            "cancel-saga-begin"
        ));
        commandHandlers.handleConfirmCancelTicket(message(
            new ConfirmCancelTicketCommand(ticket.getId()),
            "cancel-saga-confirm"
        ));

        assertEquals(
            TicketState.CANCELLED,
            ticketRepository.findById(ticket.getId()).orElseThrow().getState()
        );
    }

    @Test
    @DisplayName("Should handle CancelOrderSaga compensation")
    @Transactional
    void shouldHandleCancelOrderSagaCompensation() {
        Ticket ticket = createAndApproveTicket("cancel-comp");

        commandHandlers.handleBeginCancelTicket(message(
            new BeginCancelTicketCommand(ticket.getId()),
            "cancel-comp-begin"
        ));
        commandHandlers.handleUndoCancelTicket(message(
            new UndoCancelTicketCommand(ticket.getId()),
            "cancel-comp-undo"
        ));

        assertEquals(
            TicketState.AWAITING_ACCEPTANCE,
            ticketRepository.findById(ticket.getId()).orElseThrow().getState()
        );
    }

    @Test
    @DisplayName("Should complete ReviseOrderSaga workflow")
    @Transactional
    void shouldCompleteReviseOrderSagaWorkflow() {
        Ticket ticket = createAndApproveTicket("revise-saga");
        List<CreateTicketCommand.TicketLineItemDTO> revisedItems = Arrays.asList(
            new CreateTicketCommand.TicketLineItemDTO(1L, "Burger", 3),
            new CreateTicketCommand.TicketLineItemDTO(3L, "Salad", 1)
        );

        commandHandlers.handleBeginReviseTicket(message(
            new BeginReviseTicketCommand(ticket.getId(), revisedItems),
            "revise-saga-begin"
        ));
        commandHandlers.handleConfirmReviseTicket(message(
            new ConfirmReviseTicketCommand(ticket.getId()),
            "revise-saga-confirm"
        ));

        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertEquals(2, ticket.getLineItems().size());
        assertEquals(3, ticket.getLineItems().get(0).getQuantity());
        assertEquals("Salad", ticket.getLineItems().get(1).getName());
    }

    @Test
    @DisplayName("Should handle ReviseOrderSaga compensation")
    @Transactional
    void shouldHandleReviseOrderSagaCompensation() {
        Ticket ticket = createAndApproveTicket("revise-comp");

        commandHandlers.handleBeginReviseTicket(message(
            new BeginReviseTicketCommand(
                ticket.getId(),
                List.of(new CreateTicketCommand.TicketLineItemDTO(1L, "Burger", 3))
            ),
            "revise-comp-begin"
        ));
        commandHandlers.handleUndoReviseTicket(message(
            new UndoReviseTicketCommand(ticket.getId()),
            "revise-comp-undo"
        ));

        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertEquals(TicketState.AWAITING_ACCEPTANCE, ticket.getState());
        assertEquals(2, ticket.getLineItems().size());
    }

    @Test
    @DisplayName("Should verify ticket line items match order line items after persistence")
    @Transactional
    void shouldVerifyTicketLineItemsMatchOrderLineItemsAfterPersistence() {
        List<CreateTicketCommand.TicketLineItemDTO> orderLineItems = Arrays.asList(
            new CreateTicketCommand.TicketLineItemDTO(1L, "Burger", 2),
            new CreateTicketCommand.TicketLineItemDTO(2L, "Fries", 1),
            new CreateTicketCommand.TicketLineItemDTO(3L, "Salad", 1)
        );

        commandHandlers.handleCreateTicket(message(
            new CreateTicketCommand(ORDER_ID, RESTAURANT_ID, orderLineItems),
            "line-items-create"
        ));

        Ticket ticket = ticketRepository.findByOrderId(ORDER_ID).orElseThrow();
        assertEquals(orderLineItems.size(), ticket.getLineItems().size());
        for (int i = 0; i < orderLineItems.size(); i++) {
            CreateTicketCommand.TicketLineItemDTO orderItem = orderLineItems.get(i);
            var ticketItem = ticket.getLineItems().get(i);
            assertEquals(orderItem.getMenuItemId(), ticketItem.getMenuItemId());
            assertEquals(orderItem.getName(), ticketItem.getName());
            assertEquals(orderItem.getQuantity(), ticketItem.getQuantity());
        }
    }

    @Test
    @DisplayName("Should query tickets by restaurant and state")
    @Transactional
    void shouldQueryTicketsByRestaurantAndState() {
        for (int i = 0; i < 3; i++) {
            commandHandlers.handleCreateTicket(message(
                new CreateTicketCommand(ORDER_ID + i, RESTAURANT_ID, createSampleLineItemDTOs()),
                "query-create-" + i
            ));
        }

        Ticket ticket = ticketRepository.findByOrderId(ORDER_ID).orElseThrow();
        commandHandlers.handleApproveTicket(message(
            new ApproveTicketCommand(ticket.getId()),
            "query-approve"
        ));

        assertEquals(2, ticketRepository.findByRestaurantIdAndState(
            RESTAURANT_ID,
            TicketState.CREATE_PENDING
        ).size());
        assertEquals(1, ticketRepository.findByRestaurantIdAndState(
            RESTAURANT_ID,
            TicketState.AWAITING_ACCEPTANCE
        ).size());
    }

    private Ticket createAndApproveTicket(String prefix) {
        commandHandlers.handleCreateTicket(message(
            new CreateTicketCommand(ORDER_ID, RESTAURANT_ID, createSampleLineItemDTOs()),
            prefix + "-create"
        ));
        Ticket ticket = ticketRepository.findByOrderId(ORDER_ID).orElseThrow();
        commandHandlers.handleApproveTicket(message(
            new ApproveTicketCommand(ticket.getId()),
            prefix + "-approve"
        ));
        return ticketRepository.findById(ticket.getId()).orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private <T> CommandMessage<T> message(T command, String commandId) {
        CommandMessage<T> message = mock(CommandMessage.class);
        when(message.getCommand()).thenReturn(command);
        when(message.getMessageId()).thenReturn(commandId);
        return message;
    }
}
