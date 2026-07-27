package net.ftgo.delivery.service;

import net.ftgo.delivery.domain.Delivery;
import net.ftgo.delivery.messaging.DeliveryAssigned;
import net.ftgo.delivery.messaging.DeliveryDelivered;
import net.ftgo.delivery.messaging.DeliveryPickedUp;
import net.ftgo.delivery.messaging.DomainEventPublisher;
import net.ftgo.delivery.repository.DeliveryRepository;
import net.ftgo.delivery.security.DeliveryAuthorizationService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Transaction boundary for courier-controlled delivery operations. */
@Service
public class DeliveryService {

    private final DeliveryRepository deliveryRepository;
    private final DomainEventPublisher eventPublisher;
    private final DeliveryAuthorizationService authorizationService;

    public DeliveryService(
        DeliveryRepository deliveryRepository,
        DomainEventPublisher eventPublisher,
        DeliveryAuthorizationService authorizationService
    ) {
        this.deliveryRepository = deliveryRepository;
        this.eventPublisher = eventPublisher;
        this.authorizationService = authorizationService;
    }

    @Transactional
    public Delivery claimDelivery(Long deliveryId, Authentication authentication) {
        Long courierId = authorizationService.requireCourierId(authentication);
        Delivery delivery = requireForUpdate(deliveryId);
        delivery.assignCourier(courierId);
        deliveryRepository.saveAndFlush(delivery);
        eventPublisher.publishDeliveryEvent(
            delivery.getId(),
            delivery.getVersion(),
            new DeliveryAssigned(
                delivery.getId(),
                delivery.getOrderId(),
                delivery.getCourierId()
            )
        );
        return delivery;
    }

    @Transactional
    public Delivery pickup(Long deliveryId, Authentication authentication) {
        Delivery delivery = requireForUpdate(deliveryId);
        authorizationService.requireDeliveryAccess(delivery, authentication);
        delivery.pickup();
        deliveryRepository.saveAndFlush(delivery);
        eventPublisher.publishDeliveryEvent(
            delivery.getId(),
            delivery.getVersion(),
            new DeliveryPickedUp(
                delivery.getId(),
                delivery.getOrderId(),
                delivery.getCourierId(),
                delivery.getPickupTime()
            )
        );
        return delivery;
    }

    @Transactional
    public Delivery deliver(Long deliveryId, Authentication authentication) {
        Delivery delivery = requireForUpdate(deliveryId);
        authorizationService.requireDeliveryAccess(delivery, authentication);
        delivery.deliver();
        deliveryRepository.saveAndFlush(delivery);
        eventPublisher.publishDeliveryEvent(
            delivery.getId(),
            delivery.getVersion(),
            new DeliveryDelivered(
                delivery.getId(),
                delivery.getOrderId(),
                delivery.getCourierId(),
                delivery.getDeliveryTime()
            )
        );
        return delivery;
    }

    private Delivery requireForUpdate(Long deliveryId) {
        return deliveryRepository.findByIdForUpdate(deliveryId)
            .orElseThrow(() -> new IllegalArgumentException("Delivery not found: " + deliveryId));
    }
}
