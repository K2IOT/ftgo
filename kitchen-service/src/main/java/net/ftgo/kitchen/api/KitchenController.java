package net.ftgo.kitchen.api;

import net.ftgo.kitchen.domain.Ticket;
import net.ftgo.kitchen.domain.TicketState;
import net.ftgo.kitchen.repository.TicketRepository;
import net.ftgo.kitchen.security.TicketAuthorizationService;
import net.ftgo.kitchen.service.KitchenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/** REST API for authorized kitchen staff to manage tickets. */
@RestController
@RequestMapping("/tickets")
public class KitchenController {

    private static final Logger logger = LoggerFactory.getLogger(KitchenController.class);

    private final TicketRepository ticketRepository;
    private final KitchenService kitchenService;
    private final TicketAuthorizationService authorizationService;

    public KitchenController(
        TicketRepository ticketRepository,
        KitchenService kitchenService,
        TicketAuthorizationService authorizationService
    ) {
        this.ticketRepository = ticketRepository;
        this.kitchenService = kitchenService;
        this.authorizationService = authorizationService;
    }

    @GetMapping
    public ResponseEntity<List<TicketDTO>> getTickets(
        @RequestParam Long restaurantId,
        @RequestParam(required = false) TicketState state,
        Authentication authentication
    ) {
        authorizationService.requireRestaurantAccess(restaurantId, authentication);
        logger.info("Querying tickets for restaurant {} with state {}", restaurantId, state);

        List<Ticket> tickets = state != null
            ? ticketRepository.findByRestaurantIdAndState(restaurantId, state)
            : ticketRepository.findByRestaurantId(restaurantId);

        List<TicketDTO> ticketDTOs = tickets.stream()
            .map(TicketDTO::new)
            .collect(Collectors.toList());

        logger.info("Found {} tickets for restaurant {}", ticketDTOs.size(), restaurantId);
        return ResponseEntity.ok(ticketDTOs);
    }

    @PostMapping("/{ticketId}/accept")
    public ResponseEntity<?> acceptTicket(
        @PathVariable Long ticketId,
        Authentication authentication
    ) {
        logger.info("Accepting ticket {}", ticketId);
        try {
            Ticket ticket = kitchenService.acceptTicket(ticketId, authentication);
            return ResponseEntity.ok(new TicketDTO(ticket));
        } catch (AccessDeniedException denied) {
            throw denied;
        } catch (IllegalArgumentException error) {
            logger.error("Failed to accept ticket {}: {}", ticketId, error.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(error.getMessage()));
        } catch (IllegalStateException error) {
            logger.error("Invalid state transition for ticket {}: {}", ticketId, error.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(error.getMessage()));
        } catch (Exception error) {
            logger.error("Unexpected error accepting ticket {}", ticketId, error);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Internal error accepting ticket"));
        }
    }

    @PostMapping("/{ticketId}/preparing")
    public ResponseEntity<?> markPreparing(
        @PathVariable Long ticketId,
        Authentication authentication
    ) {
        logger.info("Marking ticket {} as preparing", ticketId);
        try {
            Ticket ticket = kitchenService.markPreparing(ticketId, authentication);
            return ResponseEntity.ok(new TicketDTO(ticket));
        } catch (AccessDeniedException denied) {
            throw denied;
        } catch (IllegalArgumentException error) {
            logger.error("Failed to mark ticket {} as preparing: {}", ticketId, error.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(error.getMessage()));
        } catch (IllegalStateException error) {
            logger.error("Invalid state transition for ticket {}: {}", ticketId, error.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(error.getMessage()));
        } catch (Exception error) {
            logger.error("Unexpected error marking ticket {} as preparing", ticketId, error);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Internal error marking ticket as preparing"));
        }
    }

    @PostMapping("/{ticketId}/ready")
    public ResponseEntity<?> markReady(
        @PathVariable Long ticketId,
        Authentication authentication
    ) {
        logger.info("Marking ticket {} as ready", ticketId);
        try {
            Ticket ticket = kitchenService.markReady(ticketId, authentication);
            return ResponseEntity.ok(new TicketDTO(ticket));
        } catch (AccessDeniedException denied) {
            throw denied;
        } catch (IllegalArgumentException error) {
            logger.error("Failed to mark ticket {} as ready: {}", ticketId, error.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(error.getMessage()));
        } catch (IllegalStateException error) {
            logger.error("Invalid state transition for ticket {}: {}", ticketId, error.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(error.getMessage()));
        } catch (Exception error) {
            logger.error("Unexpected error marking ticket {} as ready", ticketId, error);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Internal error marking ticket as ready"));
        }
    }

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
