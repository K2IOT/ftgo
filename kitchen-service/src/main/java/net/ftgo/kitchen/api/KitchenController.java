package net.ftgo.kitchen.api;

import net.ftgo.kitchen.domain.Ticket;
import net.ftgo.kitchen.domain.TicketState;
import net.ftgo.kitchen.repository.TicketRepository;
import net.ftgo.kitchen.service.KitchenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** REST API for kitchen staff to manage tickets. */
@RestController
@RequestMapping("/tickets")
public class KitchenController {

    private static final Logger logger = LoggerFactory.getLogger(KitchenController.class);

    private final TicketRepository ticketRepository;
    private final KitchenService kitchenService;

    public KitchenController(TicketRepository ticketRepository, KitchenService kitchenService) {
        this.ticketRepository = ticketRepository;
        this.kitchenService = kitchenService;
    }

    @GetMapping
    public ResponseEntity<List<TicketDTO>> getTickets(
        @RequestParam Long restaurantId,
        @RequestParam(required = false) TicketState state
    ) {
        List<Ticket> tickets = state == null
            ? ticketRepository.findByRestaurantId(restaurantId)
            : ticketRepository.findByRestaurantIdAndState(restaurantId, state);
        return ResponseEntity.ok(tickets.stream().map(TicketDTO::new).toList());
    }

    /**
     * Records the restaurant decision and returns immediately. Payment capture
     * and final acceptance are completed asynchronously by the Order saga.
     */
    @PostMapping("/{ticketId}/accept")
    public ResponseEntity<?> acceptTicket(@PathVariable Long ticketId) {
        logger.info("Requesting acceptance for ticket {}", ticketId);
        try {
            Ticket ticket = kitchenService.acceptTicket(ticketId);
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(new TicketDTO(ticket));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            logger.error("Unexpected error requesting acceptance for ticket {}", ticketId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Internal error requesting ticket acceptance"));
        }
    }

    @PostMapping("/{ticketId}/preparing")
    public ResponseEntity<?> markPreparing(@PathVariable Long ticketId) {
        try {
            return ResponseEntity.ok(new TicketDTO(kitchenService.markPreparing(ticketId)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            logger.error("Unexpected error marking ticket {} as preparing", ticketId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Internal error marking ticket as preparing"));
        }
    }

    @PostMapping("/{ticketId}/ready")
    public ResponseEntity<?> markReady(@PathVariable Long ticketId) {
        try {
            return ResponseEntity.ok(new TicketDTO(kitchenService.markReady(ticketId)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            logger.error("Unexpected error marking ticket {} as ready", ticketId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Internal error marking ticket as ready"));
        }
    }

    public static class ErrorResponse {
        private String error;

        public ErrorResponse(String error) {
            this.error = error;
        }

        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
    }
}
