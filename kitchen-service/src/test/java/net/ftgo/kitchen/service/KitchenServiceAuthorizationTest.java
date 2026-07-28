package net.ftgo.kitchen.service;

import net.ftgo.common.security.FtgoJwtAuthenticationConverter;
import net.ftgo.kitchen.domain.Ticket;
import net.ftgo.kitchen.domain.TicketLineItem;
import net.ftgo.kitchen.domain.TicketState;
import net.ftgo.kitchen.messaging.DomainEventPublisher;
import net.ftgo.kitchen.repository.TicketRepository;
import net.ftgo.kitchen.security.TicketAuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KitchenServiceAuthorizationTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private DomainEventPublisher eventPublisher;

    private KitchenService kitchenService;

    @BeforeEach
    void setUp() {
        kitchenService = new KitchenService(
            ticketRepository,
            eventPublisher,
            new TicketAuthorizationService()
        );
    }

    @Test
    void rejectsCrossRestaurantAcceptBeforeMutation() {
        Ticket ticket = awaitingAcceptanceTicket(20L);
        when(ticketRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(ticket));

        assertThrows(AccessDeniedException.class, () ->
            kitchenService.acceptTicket(1L, restaurantAuthentication(10L))
        );

        assertEquals(TicketState.AWAITING_ACCEPTANCE, ticket.getState());
        verify(ticketRepository, never()).saveAndFlush(any(Ticket.class));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void rejectsCrossRestaurantRejectBeforeMutation() {
        Ticket ticket = awaitingAcceptanceTicket(20L);
        when(ticketRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(ticket));

        assertThrows(AccessDeniedException.class, () ->
            kitchenService.rejectTicket(1L, "BUSY", restaurantAuthentication(10L))
        );

        assertEquals(TicketState.AWAITING_ACCEPTANCE, ticket.getState());
        verify(ticketRepository, never()).saveAndFlush(any(Ticket.class));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void rejectsCrossRestaurantPreparingBeforeMutation() {
        Ticket ticket = acceptedTicket(20L);
        when(ticketRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(ticket));

        assertThrows(AccessDeniedException.class, () ->
            kitchenService.markPreparing(1L, restaurantAuthentication(10L))
        );

        assertEquals(TicketState.ACCEPTED, ticket.getState());
        verify(ticketRepository, never()).saveAndFlush(any(Ticket.class));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void rejectsCrossRestaurantReadyBeforeMutation() {
        Ticket ticket = preparingTicket(20L);
        when(ticketRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(ticket));

        assertThrows(AccessDeniedException.class, () ->
            kitchenService.markReady(1L, restaurantAuthentication(10L))
        );

        assertEquals(TicketState.PREPARING, ticket.getState());
        verify(ticketRepository, never()).saveAndFlush(any(Ticket.class));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void permitsOwningRestaurantToAcceptPersistedTicket() {
        Ticket ticket = awaitingAcceptanceTicket(20L);
        when(ticketRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(ticket));
        when(ticketRepository.saveAndFlush(ticket)).thenReturn(ticket);

        Ticket accepted = kitchenService.acceptTicket(1L, restaurantAuthentication(20L));

        assertEquals(TicketState.ACCEPTED, accepted.getState());
        verify(ticketRepository).saveAndFlush(ticket);
    }

    private Ticket awaitingAcceptanceTicket(Long restaurantId) {
        Ticket ticket = new Ticket(
            restaurantId,
            123L,
            List.of(new TicketLineItem(5L, "Burger", 1))
        );
        ReflectionTestUtils.setField(ticket, "id", 1L);
        ReflectionTestUtils.setField(ticket, "version", 0L);
        ticket.approve();
        return ticket;
    }

    private Ticket acceptedTicket(Long restaurantId) {
        Ticket ticket = awaitingAcceptanceTicket(restaurantId);
        ticket.accept();
        return ticket;
    }

    private Ticket preparingTicket(Long restaurantId) {
        Ticket ticket = acceptedTicket(restaurantId);
        ticket.preparing();
        return ticket;
    }

    private AbstractAuthenticationToken restaurantAuthentication(Long restaurantId) {
        Instant now = Instant.parse("2026-07-27T00:00:00Z");
        Jwt jwt = Jwt.withTokenValue("restaurant-token")
            .header("alg", "RS256")
            .subject("restaurant-user")
            .issuedAt(now)
            .expiresAt(now.plusSeconds(300))
            .audience(List.of("ftgo-api"))
            .claim("roles", List.of("RESTAURANT"))
            .claim("restaurant_ids", List.of(restaurantId))
            .build();
        return new FtgoJwtAuthenticationConverter("").convert(jwt);
    }
}
