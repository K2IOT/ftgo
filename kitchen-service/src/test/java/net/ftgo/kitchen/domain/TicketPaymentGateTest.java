package net.ftgo.kitchen.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TicketPaymentGateTest {

    @Test
    void acceptanceRequestWaitsForPaymentCapture() {
        Ticket ticket = awaitingTicket();

        assertTrue(ticket.requestAcceptance("accept-ticket-100"));

        assertEquals(TicketState.ACCEPTANCE_PENDING_PAYMENT, ticket.getState());
        assertEquals("accept-ticket-100", ticket.getAcceptanceRequestId());
        assertNotNull(ticket.getAcceptanceRequestedAt());
        assertThrows(IllegalStateException.class, ticket::preparing);
    }

    @Test
    void duplicateAcceptanceRequestIsIdempotent() {
        Ticket ticket = awaitingTicket();

        assertTrue(ticket.requestAcceptance("accept-ticket-100"));
        assertFalse(ticket.requestAcceptance("accept-ticket-100"));

        assertThrows(IllegalStateException.class,
            () -> ticket.requestAcceptance("another-accept-request"));
    }

    @Test
    void captureConfirmationUnlocksAcceptanceAndPreparation() {
        Ticket ticket = awaitingTicket();
        ticket.requestAcceptance("accept-ticket-100");

        assertTrue(ticket.confirmAcceptance("capture-ticket-100"));
        assertFalse(ticket.confirmAcceptance("capture-ticket-100"));
        assertEquals(TicketState.ACCEPTED, ticket.getState());
        assertEquals("capture-ticket-100", ticket.getCaptureRequestId());

        ticket.preparing();
        assertEquals(TicketState.PREPARING, ticket.getState());
    }

    @Test
    void undoAcceptanceRestoresAwaitingStateBeforePreparation() {
        Ticket ticket = awaitingTicket();
        ticket.requestAcceptance("accept-ticket-100");
        ticket.confirmAcceptance("capture-ticket-100");

        assertTrue(ticket.undoAcceptance("capture declined"));
        assertFalse(ticket.undoAcceptance("capture declined"));
        assertEquals(TicketState.AWAITING_ACCEPTANCE, ticket.getState());
        assertEquals("capture declined", ticket.getAcceptanceFailureReason());
    }

    @Test
    void acceptanceCannotBeUndoneAfterPreparationBegins() {
        Ticket ticket = awaitingTicket();
        ticket.requestAcceptance("accept-ticket-100");
        ticket.confirmAcceptance("capture-ticket-100");
        ticket.preparing();

        assertThrows(IllegalStateException.class,
            () -> ticket.undoAcceptance("late compensation"));
    }

    private Ticket awaitingTicket() {
        Ticket ticket = new Ticket(
            1L,
            100L,
            List.of(new TicketLineItem(10L, "Burger", 1))
        );
        ticket.approve();
        return ticket;
    }
}
