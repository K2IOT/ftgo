package net.ftgo.kitchen.messaging;

import io.eventuate.tram.commands.consumer.CommandHandlers;
import io.eventuate.tram.commands.consumer.CommandMessage;
import io.eventuate.tram.messaging.common.Message;
import io.eventuate.tram.sagas.participant.SagaCommandHandlersBuilder;
import net.ftgo.common.channels.ChannelNames;
import net.ftgo.common.messaging.IdempotentCommandExecutor;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import static io.eventuate.tram.commands.consumer.CommandHandlerReplyBuilder.withFailure;
import static io.eventuate.tram.commands.consumer.CommandHandlerReplyBuilder.withSuccess;

/** Eventuate participant for ticket lifecycle commands. */
@Component
public class KitchenServiceCommandHandlers {

    private static final Logger logger = LoggerFactory.getLogger(KitchenServiceCommandHandlers.class);
    private static final String CONSUMER_NAME = "kitchen-service";

    private final TicketRepository ticketRepository;
    private final DomainEventPublisher eventPublisher;
    private final IdempotentCommandExecutor idempotentCommandExecutor;

    public KitchenServiceCommandHandlers(
        TicketRepository ticketRepository,
        DomainEventPublisher eventPublisher,
        IdempotentCommandExecutor idempotentCommandExecutor
    ) {
        this.ticketRepository = ticketRepository;
        this.eventPublisher = eventPublisher;
        this.idempotentCommandExecutor = idempotentCommandExecutor;
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

    public Message handleCreateTicket(CommandMessage<CreateTicketCommand> message) {
        return idempotentCommandExecutor.execute(
            CONSUMER_NAME,
            message,
            () -> createTicketOnce(message.getCommand())
        );
    }

    public Message handleApproveTicket(CommandMessage<ApproveTicketCommand> message) {
        return idempotentCommandExecutor.execute(
            CONSUMER_NAME,
            message,
            () -> approveTicketOnce(message.getCommand())
        );
    }

    public Message handleCancelTicket(CommandMessage<CancelTicketCommand> message) {
        return idempotentCommandExecutor.execute(
            CONSUMER_NAME,
            message,
            () -> cancelTicketOnce(message.getCommand())
        );
    }

    public Message handleBeginCancelTicket(CommandMessage<BeginCancelTicketCommand> message) {
        return idempotentCommandExecutor.execute(
            CONSUMER_NAME,
            message,
            () -> beginCancelTicketOnce(message.getCommand())
        );
    }

    public Message handleConfirmCancelTicket(CommandMessage<ConfirmCancelTicketCommand> message) {
        return idempotentCommandExecutor.execute(
            CONSUMER_NAME,
            message,
            () -> confirmCancelTicketOnce(message.getCommand())
        );
    }

    public Message handleUndoCancelTicket(CommandMessage<UndoCancelTicketCommand> message) {
        return idempotentCommandExecutor.execute(
            CONSUMER_NAME,
            message,
            () -> undoCancelTicketOnce(message.getCommand())
        );
    }

    public Message handleBeginReviseTicket(CommandMessage<BeginReviseTicketCommand> message) {
        return idempotentCommandExecutor.execute(
            CONSUMER_NAME,
            message,
            () -> beginReviseTicketOnce(message.getCommand())
        );
    }

    public Message handleConfirmReviseTicket(CommandMessage<ConfirmReviseTicketCommand> message) {
        return idempotentCommandExecutor.execute(
            CONSUMER_NAME,
            message,
            () -> confirmReviseTicketOnce(message.getCommand())
        );
    }

    public Message handleUndoReviseTicket(CommandMessage<UndoReviseTicketCommand> message) {
        return idempotentCommandExecutor.execute(
            CONSUMER_NAME,
            message,
            () -> undoReviseTicketOnce(message.getCommand())
        );
    }

    private Message createTicketOnce(CreateTicketCommand command) {
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
        } catch (RuntimeException e) {
            logger.error("Unexpected error creating ticket for order {}", command.getOrderId(), e);
            throw e;
        }
    }

    private Message approveTicketOnce(ApproveTicketCommand command) {
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
        } catch (RuntimeException e) {
            logger.error("Unexpected error approving ticket {}", command.getTicketId(), e);
            throw e;
        }
    }

    private Message cancelTicketOnce(CancelTicketCommand command) {
        try {
            Ticket ticket = requireForUpdate(command.getTicketId());
            if (ticket.getState() == TicketState.CANCELLED) {
                return withSuccess();
            }
            ticket.cancel();
            ticketRepository.saveAndFlush(ticket);
            eventPublisher.publishTicketEvent(
                ticket.getId(),
                ticket.getVersion(),
                new TicketCancelledEvent(ticket.getId(), ticket.getOrderId())
            );
            return withSuccess();
        } catch (IllegalArgumentException | IllegalStateException e) {
            return withFailure(e.getMessage());
        } catch (RuntimeException e) {
            logger.error("Unexpected error cancelling ticket {}", command.getTicketId(), e);
            throw e;
        }
    }

    private Message beginCancelTicketOnce(BeginCancelTicketCommand command) {
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

    private Message confirmCancelTicketOnce(ConfirmCancelTicketCommand command) {
        try {
            Ticket ticket = requireForUpdate(command.getTicketId());
            if (ticket.getState() == TicketState.CANCELLED) {
                return withSuccess();
            }
            ticket.confirmCancel();
            ticketRepository.saveAndFlush(ticket);
            eventPublisher.publishTicketEvent(
                ticket.getId(),
                ticket.getVersion(),
                new TicketCancelledEvent(ticket.getId(), ticket.getOrderId())
            );
            return withSuccess();
        } catch (IllegalArgumentException | IllegalStateException e) {
            return withFailure(e.getMessage());
        }
    }

    private Message undoCancelTicketOnce(UndoCancelTicketCommand command) {
        try {
            Ticket ticket = requireForUpdate(command.getTicketId());
            ticket.undoCancel();
            ticketRepository.save(ticket);
            return withSuccess();
        } catch (IllegalArgumentException | IllegalStateException e) {
            return withFailure(e.getMessage());
        }
    }

    private Message beginReviseTicketOnce(BeginReviseTicketCommand command) {
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

    private Message confirmReviseTicketOnce(ConfirmReviseTicketCommand command) {
        try {
            Ticket ticket = requireForUpdate(command.getTicketId());
            ticket.confirmPendingRevise();
            ticketRepository.save(ticket);
            return withSuccess();
        } catch (IllegalArgumentException | IllegalStateException e) {
            return withFailure(e.getMessage());
        }
    }

    private Message undoReviseTicketOnce(UndoReviseTicketCommand command) {
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
