package net.ftgo.kitchen.service;

import net.ftgo.kitchen.domain.Ticket;
import net.ftgo.kitchen.domain.TicketLineItem;
import net.ftgo.kitchen.domain.TicketState;
import net.ftgo.kitchen.messaging.DomainEventPublisher;
import net.ftgo.kitchen.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class KitchenServiceTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private DomainEventPublisher eventPublisher;

    private KitchenService kitchenService;

    private Ticket ticket;

    @BeforeEach
    public void setUp() {
        kitchenService = new KitchenService(ticketRepository, eventPublisher);
        
        TicketLineItem lineItem = new TicketLineItem(1L, "Burger", 2);
        ticket = new Ticket(1L, 100L, Collections.singletonList(lineItem));
    }

    @Test
    public void testAcceptTicket() {
        ticket.approve(); // Set state to AWAITING_ACCEPTANCE
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));

        Ticket updatedTicket = kitchenService.acceptTicket(1L);

        assertEquals(TicketState.ACCEPTED, updatedTicket.getState());
        verify(ticketRepository).save(ticket);
        verify(eventPublisher).publishTicketEvent(eq(ticket.getId()), any());
    }

    @Test
    public void testMarkPreparing() {
        ticket.approve();
        ticket.accept(); // Set state to ACCEPTED
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));

        Ticket updatedTicket = kitchenService.markPreparing(1L);

        assertEquals(TicketState.PREPARING, updatedTicket.getState());
        verify(ticketRepository).save(ticket);
        verify(eventPublisher).publishTicketEvent(eq(ticket.getId()), any());
    }

    @Test
    public void testMarkReady() {
        ticket.approve();
        ticket.accept();
        ticket.preparing(); // Set state to PREPARING
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));

        Ticket updatedTicket = kitchenService.markReady(1L);

        assertEquals(TicketState.READY_FOR_PICKUP, updatedTicket.getState());
        verify(ticketRepository).save(ticket);
        verify(eventPublisher).publishTicketEvent(eq(ticket.getId()), any());
    }

    @Test
    public void testAcceptTicket_NotFound() {
        when(ticketRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> {
            kitchenService.acceptTicket(1L);
        });
    }

    @Test
    public void testAcceptTicket_InvalidState() {
        // Ticket is in CREATE_PENDING state initially
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));

        assertThrows(IllegalStateException.class, () -> {
            kitchenService.acceptTicket(1L);
        });
    }
}
