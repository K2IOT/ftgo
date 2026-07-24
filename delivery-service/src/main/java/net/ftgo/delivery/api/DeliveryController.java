package net.ftgo.delivery.api;

import jakarta.validation.Valid;
import net.ftgo.delivery.domain.Delivery;
import net.ftgo.delivery.messaging.DeliveryAssigned;
import net.ftgo.delivery.messaging.DeliveryDelivered;
import net.ftgo.delivery.messaging.DeliveryPickedUp;
import net.ftgo.delivery.messaging.DomainEventPublisher;
import net.ftgo.delivery.repository.DeliveryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST API controller for delivery operations. */
@RestController
@RequestMapping("/deliveries")
public class DeliveryController {

    private static final Logger logger = LoggerFactory.getLogger(DeliveryController.class);

    private final DeliveryRepository deliveryRepository;
    private final DomainEventPublisher domainEventPublisher;

    public DeliveryController(
        DeliveryRepository deliveryRepository,
        DomainEventPublisher domainEventPublisher
    ) {
        this.deliveryRepository = deliveryRepository;
        this.domainEventPublisher = domainEventPublisher;
    }

    @GetMapping("/{deliveryId}")
    public ResponseEntity<DeliveryResponse> getDelivery(@PathVariable Long deliveryId) {
        return deliveryRepository.findById(deliveryId)
            .map(delivery -> ResponseEntity.ok(new DeliveryResponse(delivery)))
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{deliveryId}/assign")
    @Transactional
    public ResponseEntity<DeliveryResponse> assignCourier(
        @PathVariable Long deliveryId,
        @Valid @RequestBody AssignCourierRequest request
    ) {
        logger.info("Assigning courier {} to delivery {}", request.getCourierId(), deliveryId);
        Delivery delivery = requireDelivery(deliveryId);
        try {
            delivery.assignCourier(request.getCourierId());
            delivery = deliveryRepository.saveAndFlush(delivery);
            domainEventPublisher.publishDeliveryEvent(
                delivery.getId(),
                delivery.getVersion(),
                new DeliveryAssigned(
                    delivery.getId(),
                    delivery.getOrderId(),
                    delivery.getCourierId()
                )
            );
            return ResponseEntity.ok(new DeliveryResponse(delivery));
        } catch (IllegalStateException e) {
            logger.error("Failed to assign courier to delivery {}: {}", deliveryId, e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    @PostMapping("/{deliveryId}/pickup")
    @Transactional
    public ResponseEntity<DeliveryResponse> pickup(@PathVariable Long deliveryId) {
        logger.info("Marking delivery {} as picked up", deliveryId);
        Delivery delivery = requireDelivery(deliveryId);
        try {
            delivery.pickup();
            delivery = deliveryRepository.saveAndFlush(delivery);
            domainEventPublisher.publishDeliveryEvent(
                delivery.getId(),
                delivery.getVersion(),
                new DeliveryPickedUp(
                    delivery.getId(),
                    delivery.getOrderId(),
                    delivery.getCourierId(),
                    delivery.getPickupTime()
                )
            );
            return ResponseEntity.ok(new DeliveryResponse(delivery));
        } catch (IllegalStateException e) {
            logger.error("Failed to mark delivery {} as picked up: {}", deliveryId, e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    @PostMapping("/{deliveryId}/deliver")
    @Transactional
    public ResponseEntity<DeliveryResponse> deliver(@PathVariable Long deliveryId) {
        logger.info("Marking delivery {} as delivered", deliveryId);
        Delivery delivery = requireDelivery(deliveryId);
        try {
            delivery.deliver();
            delivery = deliveryRepository.saveAndFlush(delivery);
            domainEventPublisher.publishDeliveryEvent(
                delivery.getId(),
                delivery.getVersion(),
                new DeliveryDelivered(
                    delivery.getId(),
                    delivery.getOrderId(),
                    delivery.getCourierId(),
                    delivery.getDeliveryTime()
                )
            );
            return ResponseEntity.ok(new DeliveryResponse(delivery));
        } catch (IllegalStateException e) {
            logger.error("Failed to mark delivery {} as delivered: {}", deliveryId, e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    private Delivery requireDelivery(Long deliveryId) {
        return deliveryRepository.findById(deliveryId)
            .orElseThrow(() -> new DeliveryNotFoundException(deliveryId));
    }

    public static class DeliveryNotFoundException extends RuntimeException {
        public DeliveryNotFoundException(Long deliveryId) {
            super("Delivery not found: " + deliveryId);
        }
    }

    @ExceptionHandler(DeliveryNotFoundException.class)
    public ResponseEntity<String> handleDeliveryNotFound(DeliveryNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
    }
}
