package net.ftgo.kitchen.api;

import jakarta.servlet.http.HttpServletRequest;
import net.ftgo.common.web.CorrelationIdFilter;
import net.ftgo.common.web.FtgoProblemDetail;
import net.ftgo.common.web.FtgoProblemResponses;
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
        Authentication authentication,
        HttpServletRequest request
    ) {
        logger.info("Accepting ticket {}", ticketId);
        try {
            Ticket ticket = kitchenService.acceptTicket(ticketId, authentication);
            return ResponseEntity.ok(new TicketDTO(ticket));
        } catch (AccessDeniedException denied) {
            throw denied;
        } catch (IllegalArgumentException error) {
            return ticketNotFound(ticketId, request);
        } catch (IllegalStateException error) {
            return ticketConflict(ticketId, request);
        } catch (Exception error) {
            return unexpected("accept", ticketId, request, error);
        }
    }

    @PostMapping("/{ticketId}/preparing")
    public ResponseEntity<?> markPreparing(
        @PathVariable Long ticketId,
        Authentication authentication,
        HttpServletRequest request
    ) {
        logger.info("Marking ticket {} as preparing", ticketId);
        try {
            Ticket ticket = kitchenService.markPreparing(ticketId, authentication);
            return ResponseEntity.ok(new TicketDTO(ticket));
        } catch (AccessDeniedException denied) {
            throw denied;
        } catch (IllegalArgumentException error) {
            return ticketNotFound(ticketId, request);
        } catch (IllegalStateException error) {
            return ticketConflict(ticketId, request);
        } catch (Exception error) {
            return unexpected("mark preparing", ticketId, request, error);
        }
    }

    @PostMapping("/{ticketId}/ready")
    public ResponseEntity<?> markReady(
        @PathVariable Long ticketId,
        Authentication authentication,
        HttpServletRequest request
    ) {
        logger.info("Marking ticket {} as ready", ticketId);
        try {
            Ticket ticket = kitchenService.markReady(ticketId, authentication);
            return ResponseEntity.ok(new TicketDTO(ticket));
        } catch (AccessDeniedException denied) {
            throw denied;
        } catch (IllegalArgumentException error) {
            return ticketNotFound(ticketId, request);
        } catch (IllegalStateException error) {
            return ticketConflict(ticketId, request);
        } catch (Exception error) {
            return unexpected("mark ready", ticketId, request, error);
        }
    }

    private ResponseEntity<FtgoProblemDetail> ticketNotFound(
        Long ticketId,
        HttpServletRequest request
    ) {
        String correlationId = CorrelationIdFilter.current(request);
        logger.warn("Kitchen ticket not found ticketId={} correlationId={}", ticketId, correlationId);
        return FtgoProblemResponses.response(
            HttpStatus.NOT_FOUND,
            "kitchen-ticket-not-found",
            "Ticket not found",
            "The kitchen ticket was not found",
            "KITCHEN_TICKET_NOT_FOUND",
            request
        );
    }

    private ResponseEntity<FtgoProblemDetail> ticketConflict(
        Long ticketId,
        HttpServletRequest request
    ) {
        String correlationId = CorrelationIdFilter.current(request);
        logger.warn("Kitchen ticket state conflict ticketId={} correlationId={}", ticketId, correlationId);
        return FtgoProblemResponses.response(
            HttpStatus.CONFLICT,
            "kitchen-ticket-conflict",
            "Ticket state conflict",
            "Ticket cannot be changed from its current state",
            "KITCHEN_TICKET_CONFLICT",
            request
        );
    }

    private ResponseEntity<FtgoProblemDetail> unexpected(
        String operation,
        Long ticketId,
        HttpServletRequest request,
        Exception error
    ) {
        String correlationId = CorrelationIdFilter.current(request);
        logger.error(
            "Unexpected kitchen operation failure operation={} ticketId={} correlationId={}",
            operation,
            ticketId,
            correlationId,
            error
        );
        return FtgoProblemResponses.response(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "kitchen-operation-failed",
            "Kitchen operation failed",
            "The kitchen operation could not be completed",
            "KITCHEN_OPERATION_FAILED",
            request
        );
    }
}
