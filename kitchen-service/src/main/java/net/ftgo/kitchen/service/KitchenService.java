package net.ftgo.kitchen.service;

import net.ftgo.common.orderflow.events.TicketAcceptedEvent;
import net.ftgo.common.orderflow.events.TicketRejectedEvent;
import net.ftgo.kitchen.domain.Ticket;
import net.ftgo.kitchen.messaging.DomainEventPublisher;
import net.ftgo.kitchen.messaging.TicketPreparingEvent;
import net.ftgo.kitchen.messaging.TicketReadyEvent;
import net.ftgo.kitchen.repository.TicketRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transaction boundary for kitchen ticket operations.
 */
@Service
public class KitchenService {

    private final TicketRepository ticketRepository;
    private final DomainEventPublisher eventPublisher;

    public KitchenService(TicketRepository ticketRepository, DomainEventPublisher eventPublisher) {
        this.ticketRepository = ticketRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Locks the ticket row so accept, reject, and timeout decisions serialize.
     * A duplicate accepted decision returns the current aggregate without
     * emitting another outbox event.
     */
    @Transactional
    public Ticket acceptTicket(Long ticketId) {
        Ticket ticket = requireForUpdate(ticketId);
        if (ticket.accept()) {
            ticketRepository.save(ticket);
            eventPublisher.publishTicketEvent(ticket.getId(), new TicketAcceptedEvent(
                ticket.getDecisionEventId(),
                ticket.getId(),
                ticket.getOrderId(),
                ticket.getDecisionAt()
            ));
        }
        return ticket;
    }

    /**
     * Rejects a waiting ticket and emits exactly one typed decision event.
     */
    @Transactional
    public Ticket rejectTicket(Long ticketId, String reason) {
        Ticket ticket = requireForUpdate(ticketId);
        if (ticket.reject(reason)) {
            ticketRepository.save(ticket);
            eventPublisher.publishTicketEvent(ticket.getId(), new TicketRejectedEvent(
                ticket.getDecisionEventId(),
                ticket.getId(),
                ticket.getOrderId(),
                ticket.getDecisionReason(),
                ticket.getDecisionAt()
            ));
        }
        return ticket;
    }

    @Transactional
    public Ticket markPreparing(Long ticketId) {
        Ticket ticket = require(ticketId);
        ticket.preparing();
        ticketRepository.save(ticket);
        eventPublisher.publishTicketEvent(
            ticket.getId(),
            new TicketPreparingEvent(ticket.getId(), ticket.getOrderId())
        );
        return ticket;
    }

    @Transactional
    public Ticket markReady(Long ticketId) {
        Ticket ticket = require(ticketId);
        ticket.readyForPickup();
        ticketRepository.save(ticket);
        eventPublisher.publishTicketEvent(
            ticket.getId(),
            new TicketReadyEvent(ticket.getId(), ticket.getOrderId(), ticket.getReadyBy())
        );
        return ticket;
    }

    private Ticket requireForUpdate(Long ticketId) {
        return ticketRepository.findByIdForUpdate(ticketId)
            .orElseThrow(() -> new IllegalArgumentException("Ticket " + ticketId + " not found"));
    }

    private Ticket require(Long ticketId) {
        return ticketRepository.findById(ticketId)
            .orElseThrow(() -> new IllegalArgumentException("Ticket " + ticketId + " not found"));
    }
}
