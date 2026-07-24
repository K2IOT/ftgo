package net.ftgo.kitchen.service;

import net.ftgo.common.orderflow.events.TicketAcceptanceTimedOutEvent;
import net.ftgo.kitchen.domain.Ticket;
import net.ftgo.kitchen.domain.TicketLineItem;
import net.ftgo.kitchen.messaging.DomainEventPublisher;
import net.ftgo.kitchen.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketAcceptanceTimeoutServiceTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private DomainEventPublisher eventPublisher;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private TransactionStatus transactionStatus;

    private TicketAcceptanceTimeoutService service;

    @BeforeEach
    void setUp() {
        service = new TicketAcceptanceTimeoutService(
            ticketRepository,
            eventPublisher,
            transactionTemplate,
            100
        );
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            TransactionCallback<Boolean> callback = invocation.getArgument(0);
            return callback.doInTransaction(transactionStatus);
        });
    }

    @Test
    void expiredWaitingTicketPublishesOneTimeoutEvent() {
        LocalDateTime deadline = LocalDateTime.of(2026, 7, 23, 12, 0);
        Ticket ticket = awaitingTicket(901L, deadline);
        when(ticketRepository.findByIdForUpdate(901L)).thenReturn(Optional.of(ticket));

        boolean changed = service.expireOne(901L, deadline.plusSeconds(1));

        assertThat(changed).isTrue();
        ArgumentCaptor<TicketAcceptanceTimedOutEvent> event =
            ArgumentCaptor.forClass(TicketAcceptanceTimedOutEvent.class);
        verify(eventPublisher).publishTicketEvent(org.mockito.ArgumentMatchers.eq(901L), event.capture());
        assertThat(event.getValue().getTicketId()).isEqualTo(901L);
        assertThat(event.getValue().getOrderId()).isEqualTo(101L);
        assertThat(event.getValue().getAcceptanceDeadline()).isEqualTo(deadline);
    }

    @Test
    void acceptedTicketIsIgnoredWithoutDuplicateEvent() {
        LocalDateTime deadline = LocalDateTime.of(2026, 7, 23, 12, 0);
        Ticket ticket = awaitingTicket(901L, deadline);
        ticket.accept();
        when(ticketRepository.findByIdForUpdate(901L)).thenReturn(Optional.of(ticket));

        boolean changed = service.expireOne(901L, deadline.plusSeconds(1));

        assertThat(changed).isFalse();
        verify(ticketRepository, never()).save(ticket);
        verify(eventPublisher, never()).publishTicketEvent(any(), any());
    }

    private Ticket awaitingTicket(Long ticketId, LocalDateTime deadline) {
        Ticket ticket = new Ticket(
            202L,
            101L,
            List.of(new TicketLineItem(11L, "Burger", 1))
        );
        ReflectionTestUtils.setField(ticket, "id", ticketId);
        ticket.approve(deadline);
        return ticket;
    }
}
