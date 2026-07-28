package net.ftgo.kitchen.service;

import net.ftgo.common.orderflow.events.TicketAcceptedEvent;
import net.ftgo.common.orderflow.events.TicketRejectedEvent;
import net.ftgo.kitchen.domain.Ticket;
import net.ftgo.kitchen.messaging.DomainEventPublisher;
import net.ftgo.kitchen.messaging.TicketPreparingEvent;
import net.ftgo.kitchen.messaging.TicketReadyEvent;
import net.ftgo.kitchen.repository.TicketRepository;
import net.ftgo.kitchen.security.TicketAuthorizationService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Transaction boundary for kitchen ticket operations. */
@Service
public class KitchenService {

    private final TicketRepository ticketRepository;
    private final DomainEventPublisher eventPublisher;
    private final TicketAuthorizationService authorizationService;

    public KitchenService(
        TicketRepository ticketRepository,
        DomainEventPublisher eventPublisher,
        TicketAuthorizationService authorizationService
    ) {
        this.ticketRepository = ticketRepository;
        this.eventPublisher = eventPublisher;
        this.authorizationService = authorizationService;
    }

    /** Internal command-handler entrypoint; HTTP callers use the authenticated overload. */
    @Transactional
    public Ticket acceptTicket(Long ticketId) {
        return accept(requireForUpdate(ticketId));
    }

    @Transactional
    public Ticket acceptTicket(Long ticketId, Authentication authentication) {
        Ticket ticket = requireForUpdate(ticketId);
        authorizationService.requireTicketAccess(ticket, authentication);
        return accept(ticket);
    }

    private Ticket accept(Ticket ticket) {
        if (ticket.accept()) {
            ticketRepository.saveAndFlush(ticket);
            eventPublisher.publishTicketEvent(
                ticket.getId(),
                ticket.getVersion(),
                new TicketAcceptedEvent(
                    ticket.getDecisionEventId(),
                    ticket.getId(),
                    ticket.getOrderId(),
                    ticket.getDecisionAt()
                )
            );
        }
        return ticket;
    }

    /** Internal command-handler entrypoint; HTTP callers use the authenticated overload. */
    @Transactional
    public Ticket rejectTicket(Long ticketId, String reason) {
        return reject(requireForUpdate(ticketId), reason);
    }

    @Transactional
    public Ticket rejectTicket(Long ticketId, String reason, Authentication authentication) {
        Ticket ticket = requireForUpdate(ticketId);
        authorizationService.requireTicketAccess(ticket, authentication);
        return reject(ticket, reason);
    }

    private Ticket reject(Ticket ticket, String reason) {
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

    /** Internal command-handler entrypoint; HTTP callers use the authenticated overload. */
    @Transactional
    public Ticket markPreparing(Long ticketId) {
        return markPreparing(requireForUpdate(ticketId));
    }

    @Transactional
    public Ticket markPreparing(Long ticketId, Authentication authentication) {
        Ticket ticket = requireForUpdate(ticketId);
        authorizationService.requireTicketAccess(ticket, authentication);
        return markPreparing(ticket);
    }

    private Ticket markPreparing(Ticket ticket) {
        ticket.preparing();
        ticketRepository.saveAndFlush(ticket);
        eventPublisher.publishTicketEvent(
            ticket.getId(),
            ticket.getVersion(),
            new TicketPreparingEvent(ticket.getId(), ticket.getOrderId())
        );
        return ticket;
    }

    /** Internal command-handler entrypoint; HTTP callers use the authenticated overload. */
    @Transactional
    public Ticket markReady(Long ticketId) {
        return markReady(requireForUpdate(ticketId));
    }

    @Transactional
    public Ticket markReady(Long ticketId, Authentication authentication) {
        Ticket ticket = requireForUpdate(ticketId);
        authorizationService.requireTicketAccess(ticket, authentication);
        return markReady(ticket);
    }

    private Ticket markReady(Ticket ticket) {
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
