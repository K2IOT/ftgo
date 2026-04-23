package net.ftgo.kitchen.api;

import net.ftgo.kitchen.domain.Ticket;
import net.ftgo.kitchen.domain.TicketState;
import net.ftgo.kitchen.messaging.DomainEventPublisher;
import net.ftgo.kitchen.messaging.TicketAcceptedEvent;
import net.ftgo.kitchen.messaging.TicketPreparingEvent;
import net.ftgo.kitchen.messaging.TicketReadyEvent;
import net.ftgo.kitchen.repository.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST API for kitchen staff to manage tickets.
 * 
 * Endpoints:
 * - GET /tickets - Query tickets by restaurantId and state
 * - POST /tickets/{ticketId}/accept - Accept ticket
 * - POST /tickets/{ticketId}/preparing - Mark as preparing
 * - POST /tickets/{ticketId}/ready - Mark as ready for pickup
 */
@RestController
@RequestMapping("/tickets")
public class KitchenController {
    
    private static final Logger logger = LoggerFactory.getLogger(KitchenController.class);
    
    private final TicketRepository ticketRepository;
    private final DomainEventPublisher eventPublisher;
    
    public KitchenController(TicketRepository ticketRepository, DomainEventPublisher eventPublisher) {
        this.ticketRepository = ticketRepository;
        this.eventPublisher = eventPublisher;
    }
    
    /**
     * Query tickets by restaurantId and optionally by state.
     * 
     * @param restaurantId the restaurant ID (required)
     * @param state the ticket state (optional)
     * @return list of tickets
     */
    @GetMapping
    public ResponseEntity<List<TicketDTO>> getTickets(
            @RequestParam Long restaurantId,
            @RequestParam(required = false) TicketState state) {
        
        logger.info("Querying tickets for restaurant {} with state {}", restaurantId, state);
        
        List<Ticket> tickets;
        if (state != null) {
            tickets = ticketRepository.findByRestaurantIdAndState(restaurantId, state);
        } else {
            tickets = ticketRepository.findByRestaurantId(restaurantId);
        }
        
        List<TicketDTO> ticketDTOs = tickets.stream()
            .map(TicketDTO::new)
            .collect(Collectors.toList());
        
        logger.info("Found {} tickets for restaurant {}", ticketDTOs.size(), restaurantId);
        
        return ResponseEntity.ok(ticketDTOs);
    }
    
    /**
     * Accept a ticket (kitchen staff accepts the ticket).
     * Transitions ticket from AWAITING_ACCEPTANCE to ACCEPTED.
     * 
     * @param ticketId the ticket ID
     * @return the updated ticket
     */
    @PostMapping("/{ticketId}/accept")
    @Transactional
    public ResponseEntity<?> acceptTicket(@PathVariable Long ticketId) {
        logger.info("Accepting ticket {}", ticketId);
        
        try {
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
            
            return ResponseEntity.ok(new TicketDTO(ticket));
            
        } catch (IllegalArgumentException e) {
            logger.error("Failed to accept ticket {}: {}", ticketId, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(e.getMessage()));
        } catch (IllegalStateException e) {
            logger.error("Invalid state transition for ticket {}: {}", ticketId, e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            logger.error("Unexpected error accepting ticket {}", ticketId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Internal error accepting ticket"));
        }
    }
    
    /**
     * Mark ticket as preparing (kitchen staff begins preparation).
     * Transitions ticket from ACCEPTED to PREPARING.
     * 
     * @param ticketId the ticket ID
     * @return the updated ticket
     */
    @PostMapping("/{ticketId}/preparing")
    @Transactional
    public ResponseEntity<?> markPreparing(@PathVariable Long ticketId) {
        logger.info("Marking ticket {} as preparing", ticketId);
        
        try {
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
            
            return ResponseEntity.ok(new TicketDTO(ticket));
            
        } catch (IllegalArgumentException e) {
            logger.error("Failed to mark ticket {} as preparing: {}", ticketId, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(e.getMessage()));
        } catch (IllegalStateException e) {
            logger.error("Invalid state transition for ticket {}: {}", ticketId, e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            logger.error("Unexpected error marking ticket {} as preparing", ticketId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Internal error marking ticket as preparing"));
        }
    }
    
    /**
     * Mark ticket as ready for pickup.
     * Transitions ticket from PREPARING to READY_FOR_PICKUP.
     * 
     * @param ticketId the ticket ID
     * @return the updated ticket
     */
    @PostMapping("/{ticketId}/ready")
    @Transactional
    public ResponseEntity<?> markReady(@PathVariable Long ticketId) {
        logger.info("Marking ticket {} as ready", ticketId);
        
        try {
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
            
            return ResponseEntity.ok(new TicketDTO(ticket));
            
        } catch (IllegalArgumentException e) {
            logger.error("Failed to mark ticket {} as ready: {}", ticketId, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(e.getMessage()));
        } catch (IllegalStateException e) {
            logger.error("Invalid state transition for ticket {}: {}", ticketId, e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            logger.error("Unexpected error marking ticket {} as ready", ticketId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Internal error marking ticket as ready"));
        }
    }
    
    /**
     * Error response DTO.
     */
    public static class ErrorResponse {
        private String error;
        
        public ErrorResponse(String error) {
            this.error = error;
        }
        
        public String getError() {
            return error;
        }
        
        public void setError(String error) {
            this.error = error;
        }
    }
}
