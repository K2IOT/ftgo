package net.ftgo.kitchen.service;

import net.ftgo.common.orderflow.events.TicketAcceptanceRequestedEvent;
import net.ftgo.common.orderflow.events.TicketRejectedEvent;
import net.ftgo.kitchen.domain.Ticket;
import net.ftgo.kitchen.domain.TicketState;
import net.ftgo.kitchen.messaging.DomainEventPublisher;
import net.ftgo.kitchen.messaging.TicketPreparingEvent;
import net.ftgo.kitchen.messaging.TicketReadyEvent;
import net.ftgo.kitchen.repository.TicketRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Transaction boundary for kitchen ticket operations. */
@Service
public class KitchenService {

    private final TicketRepository ticketRepository;
    private final DomainEventPublisher eventPublisher;

    public KitchenService(TicketRepository ticketRepository, DomainEventPublisher eventPublisher) {
        this.ticketRepository = ticketRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Ticket acceptTicket(Long ticketId) {
        Ticket ticket = requireForUpdate(ticketId);
        if (ticket.getState() == TicketState.ACCEPTED) {
            return ticket;
        }
        String requestId = ticket.getState() == TicketState.ACCEPTANCE_PENDING_PAYMENT
            ? ticket.getAcceptanceRequestId()
            : UUID.randomUUID().toString();
        if (ticket.requestAcceptance(requestId)) {
            ticketRepository.saveAndFlush(ticket);
            eventPublisher.publishTicketEvent(
                ticket.getId(),
                ticket.getVersion(),
                new TicketAcceptanceRequestedEvent(
                    requestId,
                    ticket.getId(),
                    ticket.getOrderId(),
                    requestId,
                    ticket.getAcceptanceRequestedAt()
                )
            );
        }
        return ticket;
    }

    @Transactional
    public Ticket rejectTicket(Long ticketId, String reason) {
        Ticket ticket = requireForUpdate(ticketId);
        if (ticket.reject(reason)) {
            ticketRepository.saveAndFlush(ticket);
            eventPublisher.publishTicketEvent(
                ticket.getId(),
                ticket.getVersion(),
                new TicketRejectedEvent(
                    ticket.getDecisionEventId(),
                    ticket.getId(),
                    ticket.getOrderId(),
                    ticket.getDecisionReason(),
                    ticket.getDecisionAt()
                )
            );
        }
        return ticket;
    }

    @Transactional
    public Ticket markPreparing(Long ticketId) {
        Ticket ticket = requireForUpdate(ticketId);
        ticket.preparing();
        ticketRepository.saveAndFlush(ticket);
        eventPublisher.publishTicketEvent(
            ticket.getId(),
            ticket.getVersion(),
            new TicketPreparingEvent(ticket.getId(), ticket.getOrderId())
        );
        return ticket;
    }

    @Transactional
    public Ticket markReady(Long ticketId) {
        Ticket ticket = requireForUpdate(ticketId);
        ticket.readyForPickup();
        ticketRepository.saveAndFlush(ticket);
        eventPublisher.publishTicketEvent(
            ticket.getId(),
            ticket.getVersion(),
            new TicketReadyEvent(ticket.getId(), ticket.getOrderId(), ticket.getReadyBy())
        );
        return ticket;
    }

    private Ticket requireForUpdate(Long ticketId) {
        return ticketRepository.findByIdForUpdate(ticketId)
            .orElseThrow(() -> new IllegalArgumentException("Ticket " + ticketId + " not found"));
    }
}
