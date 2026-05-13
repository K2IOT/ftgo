package net.ftgo.kitchen.messaging;

import io.eventuate.tram.commands.consumer.CommandHandlers;
import io.eventuate.tram.commands.consumer.CommandMessage;
import io.eventuate.tram.messaging.common.Message;
import io.eventuate.tram.sagas.participant.SagaCommandHandlersBuilder;
import net.ftgo.common.orderflow.commands.ApproveTicketCommand;
import net.ftgo.common.orderflow.commands.CancelTicketCommand;
import net.ftgo.common.orderflow.commands.CreateTicketCommand;
import net.ftgo.common.orderflow.replies.TicketCreated;
import net.ftgo.kitchen.domain.Ticket;
import net.ftgo.kitchen.domain.TicketLineItem;
import net.ftgo.kitchen.repository.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

import static io.eventuate.tram.commands.consumer.CommandHandlerReplyBuilder.withFailure;
import static io.eventuate.tram.commands.consumer.CommandHandlerReplyBuilder.withSuccess;

/**
 * Command handlers for Kitchen Service saga participation.
 * 
 * Handles commands from sagas:
 * - CreateTicketCommand: Creates ticket in CREATE_PENDING state (CreateOrderSaga)
 * - ApproveTicketCommand: Transitions to AWAITING_ACCEPTANCE (CreateOrderSaga)
 * - CancelTicketCommand: Cancels ticket (compensation for CreateOrderSaga)
 * - BeginCancelTicketCommand: Begins cancellation (CancelOrderSaga)
 * - ConfirmCancelTicketCommand: Confirms cancellation (CancelOrderSaga)
 * - UndoCancelTicketCommand: Undoes cancellation (compensation for CancelOrderSaga)
 * - BeginReviseTicketCommand: Begins revision (ReviseOrderSaga)
 * - ConfirmReviseTicketCommand: Confirms revision (ReviseOrderSaga)
 * - UndoReviseTicketCommand: Undoes revision (compensation for ReviseOrderSaga)
 */
@Component
public class KitchenServiceCommandHandlers {
    
    private static final Logger logger = LoggerFactory.getLogger(KitchenServiceCommandHandlers.class);
    
    private final TicketRepository ticketRepository;
    private final DomainEventPublisher eventPublisher;
    
    public KitchenServiceCommandHandlers(TicketRepository ticketRepository,
                                        DomainEventPublisher eventPublisher) {
        this.ticketRepository = ticketRepository;
        this.eventPublisher = eventPublisher;
    }
    
    /**
     * Builds command handlers for Kitchen Service.
     * 
     * @return CommandHandlers configured for kitchenService channel
     */
    public CommandHandlers commandHandlers() {
        return SagaCommandHandlersBuilder
                .fromChannel("kitchenService")
                .onMessage(CreateTicketCommand.class, this::handleCreateTicket)
                .onMessage(ApproveTicketCommand.class, this::handleApproveTicket)
                .onMessage(CancelTicketCommand.class, this::handleCancelTicket)
                .onMessage(BeginCancelTicketCommand.class, this::handleBeginCancelTicket)
                .onMessage(ConfirmCancelTicketCommand.class, this::handleConfirmCancelTicket)
                .onMessage(UndoCancelTicketCommand.class, this::handleUndoCancelTicket)
                .onMessage(BeginReviseTicketCommand.class, this::handleBeginReviseTicket)
                .onMessage(ConfirmReviseTicketCommand.class, this::handleConfirmReviseTicket)
                .onMessage(UndoReviseTicketCommand.class, this::handleUndoReviseTicket)
                .build();
    }
    
    /**
     * Handles CreateTicketCommand from CreateOrderSaga.
     * Creates ticket in CREATE_PENDING state.
     * 
     * @param cm the command message
     * @return success reply with TicketCreated or failure reply with error message
     */
    @Transactional
    public Message handleCreateTicket(CommandMessage<CreateTicketCommand> cm) {
        CreateTicketCommand command = cm.getCommand();
        
        logger.info("Creating ticket for order {} at restaurant {}", 
            command.getOrderId(), command.getRestaurantId());
        
        try {
            // Convert DTOs to entities
            List<TicketLineItem> lineItems = command.getLineItems().stream()
                .map(dto -> new TicketLineItem(dto.getMenuItemId(), dto.getName(), dto.getQuantity()))
                .collect(Collectors.toList());
            
            // Create ticket in CREATE_PENDING state
            Ticket ticket = new Ticket(command.getRestaurantId(), command.getOrderId(), lineItems);
            ticketRepository.save(ticket);
            
            logger.info("Ticket {} created successfully for order {}", ticket.getId(), command.getOrderId());
            
            return withSuccess(new TicketCreated(ticket.getId()));
            
        } catch (IllegalArgumentException e) {
            logger.error("Failed to create ticket for order {}: {}", command.getOrderId(), e.getMessage());
            return withFailure(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error creating ticket for order {}", command.getOrderId(), e);
            return withFailure("Internal error creating ticket");
        }
    }
    
    /**
     * Handles ApproveTicketCommand from CreateOrderSaga.
     * Transitions ticket from CREATE_PENDING to AWAITING_ACCEPTANCE.
     * 
     * @param cm the command message
     * @return success reply or failure reply with error message
     */
    @Transactional
    public Message handleApproveTicket(CommandMessage<ApproveTicketCommand> cm) {
        ApproveTicketCommand command = cm.getCommand();
        
        logger.info("Approving ticket {}", command.getTicketId());
        
        try {
            Ticket ticket = ticketRepository.findById(command.getTicketId())
                .orElseThrow(() -> new IllegalArgumentException(
                    String.format("Ticket %d not found", command.getTicketId())
                ));
            
            ticket.approve();
            ticketRepository.save(ticket);
            
            logger.info("Ticket {} approved successfully", command.getTicketId());
            
            return withSuccess();
            
        } catch (IllegalArgumentException | IllegalStateException e) {
            logger.error("Failed to approve ticket {}: {}", command.getTicketId(), e.getMessage());
            return withFailure(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error approving ticket {}", command.getTicketId(), e);
            return withFailure("Internal error approving ticket");
        }
    }
    
    /**
     * Handles CancelTicketCommand (compensation for CreateOrderSaga).
     * Cancels the ticket.
     * 
     * @param cm the command message
     * @return success reply or failure reply with error message
     */
    @Transactional
    public Message handleCancelTicket(CommandMessage<CancelTicketCommand> cm) {
        CancelTicketCommand command = cm.getCommand();
        
        logger.info("Cancelling ticket {}", command.getTicketId());
        
        try {
            Ticket ticket = ticketRepository.findById(command.getTicketId())
                .orElseThrow(() -> new IllegalArgumentException(
                    String.format("Ticket %d not found", command.getTicketId())
                ));
            
            ticket.cancel();
            ticketRepository.save(ticket);
            
            // Publish TicketCancelled event
            eventPublisher.publishTicketEvent(ticket.getId(), 
                new TicketCancelledEvent(ticket.getId(), ticket.getOrderId()));
            
            logger.info("Ticket {} cancelled successfully", command.getTicketId());
            
            return withSuccess();
            
        } catch (IllegalArgumentException e) {
            logger.error("Failed to cancel ticket {}: {}", command.getTicketId(), e.getMessage());
            return withFailure(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error cancelling ticket {}", command.getTicketId(), e);
            return withFailure("Internal error cancelling ticket");
        }
    }
    
    /**
     * Handles BeginCancelTicketCommand from CancelOrderSaga.
     * Begins the cancellation process.
     * 
     * @param cm the command message
     * @return success reply or failure reply with error message
     */
    @Transactional
    public Message handleBeginCancelTicket(CommandMessage<BeginCancelTicketCommand> cm) {
        BeginCancelTicketCommand command = cm.getCommand();
        
        logger.info("Beginning cancellation for ticket {}", command.getTicketId());
        
        try {
            Ticket ticket = ticketRepository.findById(command.getTicketId())
                .orElseThrow(() -> new IllegalArgumentException(
                    String.format("Ticket %d not found", command.getTicketId())
                ));
            
            ticket.beginCancel();
            ticketRepository.save(ticket);
            
            logger.info("Cancellation begun for ticket {}", command.getTicketId());
            
            return withSuccess();
            
        } catch (IllegalArgumentException | IllegalStateException e) {
            logger.error("Failed to begin cancellation for ticket {}: {}", 
                command.getTicketId(), e.getMessage());
            return withFailure(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error beginning cancellation for ticket {}", 
                command.getTicketId(), e);
            return withFailure("Internal error beginning cancellation");
        }
    }
    
    /**
     * Handles ConfirmCancelTicketCommand from CancelOrderSaga.
     * Confirms cancellation and transitions to CANCELLED state.
     * 
     * @param cm the command message
     * @return success reply or failure reply with error message
     */
    @Transactional
    public Message handleConfirmCancelTicket(CommandMessage<ConfirmCancelTicketCommand> cm) {
        ConfirmCancelTicketCommand command = cm.getCommand();
        
        logger.info("Confirming cancellation for ticket {}", command.getTicketId());
        
        try {
            Ticket ticket = ticketRepository.findById(command.getTicketId())
                .orElseThrow(() -> new IllegalArgumentException(
                    String.format("Ticket %d not found", command.getTicketId())
                ));
            
            ticket.confirmCancel();
            ticketRepository.save(ticket);
            
            // Publish TicketCancelled event
            eventPublisher.publishTicketEvent(ticket.getId(), 
                new TicketCancelledEvent(ticket.getId(), ticket.getOrderId()));
            
            logger.info("Cancellation confirmed for ticket {}", command.getTicketId());
            
            return withSuccess();
            
        } catch (IllegalArgumentException e) {
            logger.error("Failed to confirm cancellation for ticket {}: {}", 
                command.getTicketId(), e.getMessage());
            return withFailure(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error confirming cancellation for ticket {}", 
                command.getTicketId(), e);
            return withFailure("Internal error confirming cancellation");
        }
    }
    
    /**
     * Handles UndoCancelTicketCommand (compensation for CancelOrderSaga).
     * Undoes cancellation and restores ticket to previous state.
     * 
     * @param cm the command message
     * @return success reply or failure reply with error message
     */
    @Transactional
    public Message handleUndoCancelTicket(CommandMessage<UndoCancelTicketCommand> cm) {
        UndoCancelTicketCommand command = cm.getCommand();
        
        logger.info("Undoing cancellation for ticket {}", command.getTicketId());
        
        try {
            Ticket ticket = ticketRepository.findById(command.getTicketId())
                .orElseThrow(() -> new IllegalArgumentException(
                    String.format("Ticket %d not found", command.getTicketId())
                ));
            
            ticket.undoCancel();
            ticketRepository.save(ticket);
            
            logger.info("Cancellation undone for ticket {}", command.getTicketId());
            
            return withSuccess();
            
        } catch (IllegalArgumentException e) {
            logger.error("Failed to undo cancellation for ticket {}: {}", 
                command.getTicketId(), e.getMessage());
            return withFailure(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error undoing cancellation for ticket {}", 
                command.getTicketId(), e);
            return withFailure("Internal error undoing cancellation");
        }
    }
    
    /**
     * Handles BeginReviseTicketCommand from ReviseOrderSaga.
     * Begins the revision process.
     * 
     * @param cm the command message
     * @return success reply or failure reply with error message
     */
    @Transactional
    public Message handleBeginReviseTicket(CommandMessage<BeginReviseTicketCommand> cm) {
        BeginReviseTicketCommand command = cm.getCommand();
        
        logger.info("Beginning revision for ticket {}", command.getTicketId());
        
        try {
            Ticket ticket = ticketRepository.findById(command.getTicketId())
                .orElseThrow(() -> new IllegalArgumentException(
                    String.format("Ticket %d not found", command.getTicketId())
                ));
            
            // Convert DTOs to entities
            List<TicketLineItem> revisedLineItems = command.getRevisedLineItems().stream()
                .map(dto -> new TicketLineItem(dto.getMenuItemId(), dto.getName(), dto.getQuantity()))
                .collect(Collectors.toList());
            
            ticket.beginRevise(revisedLineItems);
            ticketRepository.save(ticket);
            
            logger.info("Revision begun for ticket {}", command.getTicketId());
            
            return withSuccess();
            
        } catch (IllegalArgumentException | IllegalStateException e) {
            logger.error("Failed to begin revision for ticket {}: {}", 
                command.getTicketId(), e.getMessage());
            return withFailure(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error beginning revision for ticket {}", 
                command.getTicketId(), e);
            return withFailure("Internal error beginning revision");
        }
    }
    
    /**
     * Handles ConfirmReviseTicketCommand from ReviseOrderSaga.
     * Confirms revision and updates line items.
     * 
     * @param cm the command message
     * @return success reply or failure reply with error message
     */
    @Transactional
    public Message handleConfirmReviseTicket(CommandMessage<ConfirmReviseTicketCommand> cm) {
        ConfirmReviseTicketCommand command = cm.getCommand();
        
        logger.info("Confirming revision for ticket {}", command.getTicketId());
        
        try {
            Ticket ticket = ticketRepository.findById(command.getTicketId())
                .orElseThrow(() -> new IllegalArgumentException(
                    String.format("Ticket %d not found", command.getTicketId())
                ));
            
            // Convert DTOs to entities
            List<TicketLineItem> revisedLineItems = command.getRevisedLineItems().stream()
                .map(dto -> new TicketLineItem(dto.getMenuItemId(), dto.getName(), dto.getQuantity()))
                .collect(Collectors.toList());
            
            ticket.confirmRevise(revisedLineItems);
            ticketRepository.save(ticket);
            
            logger.info("Revision confirmed for ticket {}", command.getTicketId());
            
            return withSuccess();
            
        } catch (IllegalArgumentException e) {
            logger.error("Failed to confirm revision for ticket {}: {}", 
                command.getTicketId(), e.getMessage());
            return withFailure(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error confirming revision for ticket {}", 
                command.getTicketId(), e);
            return withFailure("Internal error confirming revision");
        }
    }
    
    /**
     * Handles UndoReviseTicketCommand (compensation for ReviseOrderSaga).
     * Restores ticket from REVISION_PENDING to its previous state.
     * Line items are not modified because beginRevise() only sets the pending state.
     * 
     * @param cm the command message
     * @return success reply or failure reply with error message
     */
    @Transactional
    public Message handleUndoReviseTicket(CommandMessage<UndoReviseTicketCommand> cm) {
        UndoReviseTicketCommand command = cm.getCommand();
        
        logger.info("Undoing revision for ticket {}", command.getTicketId());
        
        try {
            Ticket ticket = ticketRepository.findById(command.getTicketId())
                .orElseThrow(() -> new IllegalArgumentException(
                    String.format("Ticket %d not found", command.getTicketId())
                ));
            
            ticket.undoRevise();
            ticketRepository.save(ticket);
            
            logger.info("Revision undone for ticket {}", command.getTicketId());
            
            return withSuccess();
            
        } catch (IllegalArgumentException e) {
            logger.error("Failed to undo revision for ticket {}: {}", 
                command.getTicketId(), e.getMessage());
            return withFailure(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error undoing revision for ticket {}", 
                command.getTicketId(), e);
            return withFailure("Internal error undoing revision");
        }
    }
}
