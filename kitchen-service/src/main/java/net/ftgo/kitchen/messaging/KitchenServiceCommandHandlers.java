package net.ftgo.kitchen.messaging;

import io.eventuate.tram.commands.consumer.CommandHandlers;
import io.eventuate.tram.commands.consumer.CommandMessage;
import io.eventuate.tram.messaging.common.Message;
import io.eventuate.tram.sagas.participant.SagaCommandHandlersBuilder;
import net.ftgo.common.channels.ChannelNames;
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
import net.ftgo.common.orderflow.replies.TicketCreated;
import net.ftgo.common.orderflow.replies.TicketRevisionRefused;
import net.ftgo.kitchen.domain.Ticket;
import net.ftgo.kitchen.domain.TicketLineItem;
import net.ftgo.kitchen.domain.TicketState;
import net.ftgo.kitchen.repository.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import static io.eventuate.tram.commands.consumer.CommandHandlerReplyBuilder.withFailure;
import static io.eventuate.tram.commands.consumer.CommandHandlerReplyBuilder.withSuccess;

/**
 * Eventuate participant for ticket lifecycle commands.
 *
 * <p>Every handler uses a business identifier to make duplicate commands a
 * no-op or return the established result.</p>
 */
@Component
public class KitchenServiceCommandHandlers {

    private static final Logger logger = LoggerFactory.getLogger(KitchenServiceCommandHandlers.class);

    private final TicketRepository ticketRepository;
    private final DomainEventPublisher eventPublisher;

    public KitchenServiceCommandHandlers(
        TicketRepository ticketRepository,
        DomainEventPublisher eventPublisher
    ) {
        this.ticketRepository = ticketRepository;
        this.eventPublisher = eventPublisher;
    }

    public CommandHandlers commandHandlers() {
        return SagaCommandHandlersBuilder
            .fromChannel(ChannelNames.KITCHEN_SERVICE_COMMAND_CHANNEL)
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

    @Transactional
    public Message handleCreateTicket(CommandMessage<CreateTicketCommand> message) {
        CreateTicketCommand command = message.getCommand();
        try {
            Ticket existing = ticketRepository.findByOrderId(command.getOrderId()).orElse(null);
            if (existing != null) {
                if (!Objects.equals(existing.getRestaurantId(), command.getRestaurantId())) {
                    return withFailure("Order already has a ticket for another restaurant");
                }
                return withSuccess(new TicketCreated(existing.getId()));
            }

            List<TicketLineItem> lineItems = command.getLineItems().stream()
                .map(item -> new TicketLineItem(
                    item.getMenuItemId(),
                    item.getName(),
                    item.getQuantity()
                ))
                .toList();
            Ticket ticket = ticketRepository.save(new Ticket(
                command.getRestaurantId(),
                command.getOrderId(),
                lineItems
            ));
            return withSuccess(new TicketCreated(ticket.getId()));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return withFailure(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error creating ticket for order {}", command.getOrderId(), e);
            return withFailure("Internal error creating ticket");
        }
    }

    @Transactional
    public Message handleApproveTicket(CommandMessage<ApproveTicketCommand> message) {
        ApproveTicketCommand command = message.getCommand();
        try {
            Ticket ticket = requireForUpdate(command.getTicketId());
            LocalDateTime deadline = command.getAcceptanceDeadline();

            if (ticket.getState() == TicketState.AWAITING_ACCEPTANCE) {
                if (deadline == null || Objects.equals(deadline, ticket.getAcceptanceDeadline())) {
                    return withSuccess();
                }
                return withFailure("Ticket is already awaiting another acceptance deadline");
            }

            if (deadline == null) {
                ticket.approve();
            } else {
                ticket.approve(deadline);
            }
            ticketRepository.save(ticket);
            return withSuccess();
        } catch (IllegalArgumentException | IllegalStateException e) {
            return withFailure(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error approving ticket {}", command.getTicketId(), e);
            return withFailure("Internal error approving ticket");
        }
    }

    @Transactional
    public Message handleCancelTicket(CommandMessage<CancelTicketCommand> message) {
        CancelTicketCommand command = message.getCommand();
        try {
            Ticket ticket = requireForUpdate(command.getTicketId());
            if (ticket.getState() == TicketState.CANCELLED) {
                return withSuccess();
            }
            ticket.cancel();
            ticketRepository.save(ticket);
            eventPublisher.publishTicketEvent(
                ticket.getId(),
                new TicketCancelledEvent(ticket.getId(), ticket.getOrderId())
            );
            return withSuccess();
        } catch (IllegalArgumentException | IllegalStateException e) {
            return withFailure(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error cancelling ticket {}", command.getTicketId(), e);
            return withFailure("Internal error cancelling ticket");
        }
    }

    @Transactional
    public Message handleBeginCancelTicket(CommandMessage<BeginCancelTicketCommand> message) {
        BeginCancelTicketCommand command = message.getCommand();
        try {
            Ticket ticket = requireForUpdate(command.getTicketId());
            if (ticket.getState() == TicketState.CANCEL_PENDING) {
                return withSuccess();
            }
            ticket.beginCancel();
            ticketRepository.save(ticket);
            return withSuccess();
        } catch (IllegalStateException e) {
            if ("Cannot cancel ticket after preparation has begun".equals(e.getMessage())) {
                return withFailure(new TicketCancellationRefused(
                    TicketCancellationRefused.PREPARATION_ALREADY_STARTED,
                    e.getMessage()
                ));
            }
            return withFailure(e.getMessage());
        } catch (IllegalArgumentException e) {
            return withFailure(e.getMessage());
        }
    }

    @Transactional
    public Message handleConfirmCancelTicket(CommandMessage<ConfirmCancelTicketCommand> message) {
        ConfirmCancelTicketCommand command = message.getCommand();
        try {
            Ticket ticket = requireForUpdate(command.getTicketId());
            if (ticket.getState() == TicketState.CANCELLED) {
                return withSuccess();
            }
            ticket.confirmCancel();
            ticketRepository.save(ticket);
            eventPublisher.publishTicketEvent(
                ticket.getId(),
                new TicketCancelledEvent(ticket.getId(), ticket.getOrderId())
            );
            return withSuccess();
        } catch (IllegalArgumentException | IllegalStateException e) {
            return withFailure(e.getMessage());
        }
    }

    @Transactional
    public Message handleUndoCancelTicket(CommandMessage<UndoCancelTicketCommand> message) {
        UndoCancelTicketCommand command = message.getCommand();
        try {
            Ticket ticket = requireForUpdate(command.getTicketId());
            ticket.undoCancel();
            ticketRepository.save(ticket);
            return withSuccess();
        } catch (IllegalArgumentException | IllegalStateException e) {
            return withFailure(e.getMessage());
        }
    }

    @Transactional
    public Message handleBeginReviseTicket(CommandMessage<BeginReviseTicketCommand> message) {
        BeginReviseTicketCommand command = message.getCommand();
        try {
            Ticket ticket = requireForUpdate(command.getTicketId());
            if (ticket.getState() == TicketState.REVISION_PENDING) {
                return withSuccess();
            }
            List<TicketLineItem> revisedLineItems = command.getRevisedLineItems().stream()
                .map(item -> new TicketLineItem(
                    item.getMenuItemId(),
                    item.getName(),
                    item.getQuantity()
                ))
                .toList();
            ticket.beginRevise(revisedLineItems);
            ticketRepository.save(ticket);
            return withSuccess();
        } catch (IllegalStateException e) {
            if ("Cannot revise ticket after preparation has begun".equals(e.getMessage())) {
                return withFailure(new TicketRevisionRefused(
                    TicketRevisionRefused.PREPARATION_ALREADY_STARTED,
                    e.getMessage()
                ));
            }
            return withFailure(e.getMessage());
        } catch (IllegalArgumentException e) {
            return withFailure(e.getMessage());
        }
    }

    @Transactional
    public Message handleConfirmReviseTicket(CommandMessage<ConfirmReviseTicketCommand> message) {
        ConfirmReviseTicketCommand command = message.getCommand();
        try {
            Ticket ticket = requireForUpdate(command.getTicketId());
            ticket.confirmPendingRevise();
            ticketRepository.save(ticket);
            return withSuccess();
        } catch (IllegalArgumentException | IllegalStateException e) {
            return withFailure(e.getMessage());
        }
    }

    @Transactional
    public Message handleUndoReviseTicket(CommandMessage<UndoReviseTicketCommand> message) {
        UndoReviseTicketCommand command = message.getCommand();
        try {
            Ticket ticket = requireForUpdate(command.getTicketId());
            ticket.undoRevise();
            ticketRepository.save(ticket);
            return withSuccess();
        } catch (IllegalArgumentException | IllegalStateException e) {
            return withFailure(e.getMessage());
        }
    }

    private Ticket requireForUpdate(Long ticketId) {
        return ticketRepository.findByIdForUpdate(ticketId)
            .orElseThrow(() -> new IllegalArgumentException("Ticket " + ticketId + " not found"));
    }
}
