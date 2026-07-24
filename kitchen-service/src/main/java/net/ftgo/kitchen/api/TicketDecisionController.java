package net.ftgo.kitchen.api;

import net.ftgo.kitchen.domain.Ticket;
import net.ftgo.kitchen.service.KitchenService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Restaurant decision endpoints kept separate from preparation endpoints.
 */
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
        @RequestBody(required = false) RejectTicketRequest request
    ) {
        try {
            String reason = request == null ? null : request.reason();
            Ticket ticket = kitchenService.rejectTicket(ticketId, reason);
            return ResponseEntity.ok(new TicketDTO(ticket));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new KitchenController.ErrorResponse(e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new KitchenController.ErrorResponse(e.getMessage()));
        }
    }

    public record RejectTicketRequest(String reason) {
    }
}
