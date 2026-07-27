package net.ftgo.delivery.service;

import net.ftgo.common.Address;
import net.ftgo.common.security.FtgoJwtAuthenticationConverter;
import net.ftgo.delivery.domain.Delivery;
import net.ftgo.delivery.domain.DeliveryStatus;
import net.ftgo.delivery.messaging.DomainEventPublisher;
import net.ftgo.delivery.repository.DeliveryRepository;
import net.ftgo.delivery.security.DeliveryAuthorizationService;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliveryServiceAuthorizationTest {

    @Mock
    private DeliveryRepository deliveryRepository;

    @Mock
    private DomainEventPublisher eventPublisher;

    private DeliveryService deliveryService;

    @BeforeEach
    void setUp() {
        deliveryService = new DeliveryService(
            deliveryRepository,
            eventPublisher,
            new DeliveryAuthorizationService()
        );
    }

    @Test
    void claimsPendingDeliveryUsingCourierIdentityFromPrincipal() {
        Delivery delivery = pendingDelivery();
        when(deliveryRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(delivery));
        when(deliveryRepository.saveAndFlush(delivery)).thenReturn(delivery);

        Delivery claimed = deliveryService.claimDelivery(1L, courierAuthentication(77L));

        assertEquals(77L, claimed.getCourierId());
        assertEquals(DeliveryStatus.ASSIGNED, claimed.getStatus());
        verify(deliveryRepository).findByIdForUpdate(1L);
        verify(deliveryRepository).saveAndFlush(delivery);
        verify(eventPublisher).publishDeliveryEvent(eq(1L), eq(delivery.getVersion()), any());
    }

    @Test
    void secondCourierCannotClaimAlreadyAssignedDelivery() {
        Delivery delivery = pendingDelivery();
        delivery.assignCourier(77L);
        when(deliveryRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(delivery));

        assertThrows(
            IllegalStateException.class,
            () -> deliveryService.claimDelivery(1L, courierAuthentication(88L))
        );

        assertEquals(77L, delivery.getCourierId());
        verify(deliveryRepository, never()).saveAndFlush(any(Delivery.class));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void differentCourierCannotPickupAssignedDelivery() {
        Delivery delivery = assignedDelivery(77L);
        when(deliveryRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(delivery));

        assertThrows(
            AccessDeniedException.class,
            () -> deliveryService.pickup(1L, courierAuthentication(88L))
        );

        assertEquals(DeliveryStatus.ASSIGNED, delivery.getStatus());
        verify(deliveryRepository, never()).saveAndFlush(any(Delivery.class));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void assignedCourierCanPickupAndDeliver() {
        Delivery delivery = assignedDelivery(77L);
        when(deliveryRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(delivery));
        when(deliveryRepository.saveAndFlush(delivery)).thenReturn(delivery);

        Delivery pickedUp = deliveryService.pickup(1L, courierAuthentication(77L));
        assertEquals(DeliveryStatus.PICKED_UP, pickedUp.getStatus());

        Delivery delivered = deliveryService.deliver(1L, courierAuthentication(77L));
        assertEquals(DeliveryStatus.DELIVERED, delivered.getStatus());
    }

    private Delivery pendingDelivery() {
        Delivery delivery = new Delivery(
            123L,
            new Address("1 Pickup St", "Austin", "TX", "78701"),
            new Address("2 Dropoff St", "Austin", "TX", "78702"),
            LocalDateTime.now().plusHours(1)
        );
        ReflectionTestUtils.setField(delivery, "id", 1L);
        ReflectionTestUtils.setField(delivery, "version", 0L);
        return delivery;
    }

    private Delivery assignedDelivery(Long courierId) {
        Delivery delivery = pendingDelivery();
        delivery.assignCourier(courierId);
        return delivery;
    }

    private AbstractAuthenticationToken courierAuthentication(Long courierId) {
        Instant now = Instant.parse("2026-07-27T00:00:00Z");
        Jwt jwt = Jwt.withTokenValue("courier-" + courierId + "-token")
            .header("alg", "RS256")
            .subject("courier-" + courierId)
            .issuedAt(now)
            .expiresAt(now.plusSeconds(300))
            .audience(List.of("ftgo-api"))
            .claim("roles", List.of("COURIER"))
            .claim("courier_id", courierId)
            .build();
        return new FtgoJwtAuthenticationConverter("").convert(jwt);
    }
}
