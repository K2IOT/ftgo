package net.ftgo.kitchen.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TicketAcceptanceDecisionTest {

    @Test
    void exactlyOneAcceptDecisionWinsAndDuplicateIsNoOp() {
        LocalDateTime deadline = LocalDateTime.of(2026, 7, 23, 12, 0);
        Ticket ticket = awaitingAcceptance(deadline);

        assertThat(ticket.accept()).isTrue();
        assertThat(ticket.accept()).isFalse();

        assertThat(ticket.getState()).isEqualTo(TicketState.ACCEPTED);
        assertThat(ticket.getDecisionEventId()).isNotBlank();
        assertThat(ticket.getDecisionAt()).isNotNull();
        assertThatThrownBy(() -> ticket.reject("CAPACITY"))
            .isInstanceOf(IllegalStateException.class);
        assertThat(ticket.timeout(deadline.plusMinutes(1))).isFalse();
    }

    @Test
    void explicitRejectWinsAndIsIdempotentForSameReason() {
        Ticket ticket = awaitingAcceptance(LocalDateTime.now().plusMinutes(5));

        assertThat(ticket.reject("CAPACITY")).isTrue();
        assertThat(ticket.reject("CAPACITY")).isFalse();

        assertThat(ticket.getState()).isEqualTo(TicketState.REJECTED_BY_RESTAURANT);
        assertThat(ticket.getDecisionReason()).isEqualTo("CAPACITY");
        assertThatThrownBy(ticket::accept).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void timeoutOnlyWinsAfterDeadlineAndDuplicateIsNoOp() {
        LocalDateTime deadline = LocalDateTime.of(2026, 7, 23, 12, 0);
        Ticket ticket = awaitingAcceptance(deadline);

        assertThat(ticket.timeout(deadline.minusSeconds(1))).isFalse();
        assertThat(ticket.timeout(deadline)).isTrue();
        assertThat(ticket.timeout(deadline.plusMinutes(1))).isFalse();

        assertThat(ticket.getState()).isEqualTo(TicketState.REJECTED_TIMEOUT);
        assertThat(ticket.getAcceptanceDeadline()).isEqualTo(deadline);
        assertThat(ticket.getDecisionReason()).isEqualTo("ACCEPTANCE_TIMEOUT");
        assertThatThrownBy(ticket::accept).isInstanceOf(IllegalStateException.class);
    }

    private Ticket awaitingAcceptance(LocalDateTime deadline) {
        Ticket ticket = new Ticket(
            202L,
            101L,
            List.of(new TicketLineItem(11L, "Burger", 1))
        );
        ticket.approve(deadline);
        return ticket;
    }
}
