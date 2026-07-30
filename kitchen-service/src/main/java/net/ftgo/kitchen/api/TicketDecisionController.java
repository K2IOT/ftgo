package net.ftgo.kitchen.api;

import jakarta.servlet.http.HttpServletRequest;
import net.ftgo.common.web.FtgoProblemDetail;
import net.ftgo.common.web.FtgoProblemResponses;
import net.ftgo.kitchen.domain.Ticket;
import net.ftgo.kitchen.service.KitchenService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Restaurant decision endpoints kept separate from preparation endpoints. */
@RestController
@RequestMapping("/tickets")
public class TicketDecisionController {

    private final KitchenService kitchenService;

    public TicketDecisionController(KitchenService kitchenService) {
        this.kitchenService = kitchenService;
    }

    @PostMapping("/{ticketId}/reject")
    public ResponseEntity<?> rejectTicket(
        @PathVariable Long ticketId,
        @RequestBody(required = false) RejectTicketRequest request,
        HttpServletRequest servletRequest
    ) {
        try {
            String reason = request == null ? null : request.reason();
            Ticket ticket = kitchenService.rejectTicket(ticketId, reason);
            return ResponseEntity.ok(new TicketDTO(ticket));
        } catch (IllegalArgumentException error) {
            return ticketNotFound(servletRequest);
        } catch (IllegalStateException error) {
            return ticketConflict(servletRequest);
        }
    }

    private ResponseEntity<FtgoProblemDetail> ticketNotFound(HttpServletRequest request) {
        return FtgoProblemResponses.response(
            HttpStatus.NOT_FOUND,
            "kitchen-ticket-not-found",
            "Ticket not found",
            "The kitchen ticket was not found",
            "KITCHEN_TICKET_NOT_FOUND",
            request
        );
    }

    private ResponseEntity<FtgoProblemDetail> ticketConflict(HttpServletRequest request) {
        return FtgoProblemResponses.response(
            HttpStatus.CONFLICT,
            "kitchen-ticket-conflict",
            "Ticket state conflict",
            "Ticket cannot be changed from its current state",
            "KITCHEN_TICKET_CONFLICT",
            request
        );
    }

    public record RejectTicketRequest(String reason) {
    }
}
