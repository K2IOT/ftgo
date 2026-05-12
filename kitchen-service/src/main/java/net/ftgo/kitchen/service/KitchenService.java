package net.ftgo.kitchen.service;

import net.ftgo.kitchen.domain.Ticket;
import net.ftgo.kitchen.messaging.DomainEventPublisher;
import net.ftgo.kitchen.messaging.TicketAcceptedEvent;
import net.ftgo.kitchen.messaging.TicketPreparingEvent;
import net.ftgo.kitchen.messaging.TicketReadyEvent;
import net.ftgo.kitchen.repository.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application Service for managing kitchen tickets.
 * Encapsulates the business transactions for ticket state transitions.
 */
@Service
public class KitchenService {

    private static final Logger logger = LoggerFactory.getLogger(KitchenService.class);

    private final TicketRepository ticketRepository;
    private final DomainEventPublisher eventPublisher;

    public KitchenService(TicketRepository ticketRepository, DomainEventPublisher eventPublisher) {
        this.ticketRepository = ticketRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Accepts a ticket.
     * Transitions ticket from AWAITING_ACCEPTANCE to ACCEPTED.
     *
     * @param ticketId the ticket ID
     * @return the updated ticket
     * @throws IllegalArgumentException if the ticket is not found
     * @throws IllegalStateException if the ticket is not in AWAITING_ACCEPTANCE state
     */
    @Transactional
    public Ticket acceptTicket(Long ticketId) {
        logger.info("Accepting ticket {}", ticketId);

        Ticket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new IllegalArgumentException(
                String.format("Ticket %d not found", ticketId)
            ));

        ticket.accept();
        ticketRepository.save(ticket);

        // Publish TicketAccepted event
        eventPublisher.publishTicketEvent(ticket.getId(),
            new TicketAcceptedEvent(ticket.getId(), ticket.getOrderId(), ticket.getAcceptedAt()));

        logger.info("Ticket {} accepted successfully", ticketId);
        
        return ticket;
    }

    /**
     * Marks a ticket as preparing.
     * Transitions ticket from ACCEPTED to PREPARING.
     *
     * @param ticketId the ticket ID
     * @return the updated ticket
     * @throws IllegalArgumentException if the ticket is not found
     * @throws IllegalStateException if the ticket is not in ACCEPTED state
     */
    @Transactional
    public Ticket markPreparing(Long ticketId) {
        logger.info("Marking ticket {} as preparing", ticketId);

        Ticket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new IllegalArgumentException(
                String.format("Ticket %d not found", ticketId)
            ));

        ticket.preparing();
        ticketRepository.save(ticket);

        // Publish TicketPreparing event
        eventPublisher.publishTicketEvent(ticket.getId(),
            new TicketPreparingEvent(ticket.getId(), ticket.getOrderId()));

        logger.info("Ticket {} marked as preparing", ticketId);
        
        return ticket;
    }

    /**
     * Marks a ticket as ready for pickup.
     * Transitions ticket from PREPARING to READY_FOR_PICKUP.
     *
     * @param ticketId the ticket ID
     * @return the updated ticket
     * @throws IllegalArgumentException if the ticket is not found
     * @throws IllegalStateException if the ticket is not in PREPARING state
     */
    @Transactional
    public Ticket markReady(Long ticketId) {
        logger.info("Marking ticket {} as ready", ticketId);

        Ticket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new IllegalArgumentException(
                String.format("Ticket %d not found", ticketId)
            ));

        ticket.readyForPickup();
        ticketRepository.save(ticket);

        // Publish TicketReady event
        eventPublisher.publishTicketEvent(ticket.getId(),
            new TicketReadyEvent(ticket.getId(), ticket.getOrderId(), ticket.getReadyBy()));

        logger.info("Ticket {} marked as ready", ticketId);
        
        return ticket;
    }
}
