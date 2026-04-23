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
import org.springframework.web.bind.annotation.*;

/**
 * REST API controller for delivery operations.
 * 
 * Provides endpoints for courier assignment, pickup, and delivery completion.
 */
@RestController
@RequestMapping("/deliveries")
public class DeliveryController {
    
    private static final Logger logger = LoggerFactory.getLogger(DeliveryController.class);
    
    private final DeliveryRepository deliveryRepository;
    private final DomainEventPublisher domainEventPublisher;
    
    public DeliveryController(DeliveryRepository deliveryRepository,
                             DomainEventPublisher domainEventPublisher) {
        this.deliveryRepository = deliveryRepository;
        this.domainEventPublisher = domainEventPublisher;
    }
    
    /**
     * Gets delivery information by ID.
     * 
     * @param deliveryId the delivery ID
     * @return delivery response
     */
    @GetMapping("/{deliveryId}")
    public ResponseEntity<DeliveryResponse> getDelivery(@PathVariable Long deliveryId) {
        return deliveryRepository.findById(deliveryId)
            .map(delivery -> ResponseEntity.ok(new DeliveryResponse(delivery)))
            .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * Assigns a courier to a delivery.
     * 
     * POST /deliveries/{deliveryId}/assign
     * 
     * @param deliveryId the delivery ID
     * @param request the assign courier request
     * @return updated delivery response
     */
    @PostMapping("/{deliveryId}/assign")
    @Transactional
    public ResponseEntity<DeliveryResponse> assignCourier(
            @PathVariable Long deliveryId,
            @Valid @RequestBody AssignCourierRequest request) {
        
        logger.info("Assigning courier {} to delivery {}", request.getCourierId(), deliveryId);
        
        Delivery delivery = deliveryRepository.findById(deliveryId)
            .orElseThrow(() -> new DeliveryNotFoundException(deliveryId));
        
        try {
            delivery.assignCourier(request.getCourierId());
            deliveryRepository.save(delivery);
            
            // Publish DeliveryAssigned event
            DeliveryAssigned event = new DeliveryAssigned(
                delivery.getId(),
                delivery.getOrderId(),
                delivery.getCourierId()
            );
            domainEventPublisher.publishDeliveryEvent(delivery.getId(), event);
            
            logger.info("Assigned courier {} to delivery {}", request.getCourierId(), deliveryId);
            
            return ResponseEntity.ok(new DeliveryResponse(delivery));
        } catch (IllegalStateException e) {
            logger.error("Failed to assign courier to delivery {}: {}", deliveryId, e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }
    
    /**
     * Marks a delivery as picked up by the courier.
     * 
     * POST /deliveries/{deliveryId}/pickup
     * 
     * @param deliveryId the delivery ID
     * @return updated delivery response
     */
    @PostMapping("/{deliveryId}/pickup")
    @Transactional
    public ResponseEntity<DeliveryResponse> pickup(@PathVariable Long deliveryId) {
        logger.info("Marking delivery {} as picked up", deliveryId);
        
        Delivery delivery = deliveryRepository.findById(deliveryId)
            .orElseThrow(() -> new DeliveryNotFoundException(deliveryId));
        
        try {
            delivery.pickup();
            deliveryRepository.save(delivery);
            
            // Publish DeliveryPickedUp event
            DeliveryPickedUp event = new DeliveryPickedUp(
                delivery.getId(),
                delivery.getOrderId(),
                delivery.getCourierId(),
                delivery.getPickupTime()
            );
            domainEventPublisher.publishDeliveryEvent(delivery.getId(), event);
            
            logger.info("Marked delivery {} as picked up at {}", deliveryId, delivery.getPickupTime());
            
            return ResponseEntity.ok(new DeliveryResponse(delivery));
        } catch (IllegalStateException e) {
            logger.error("Failed to mark delivery {} as picked up: {}", deliveryId, e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }
    
    /**
     * Marks a delivery as delivered to the consumer.
     * 
     * POST /deliveries/{deliveryId}/deliver
     * 
     * @param deliveryId the delivery ID
     * @return updated delivery response
     */
    @PostMapping("/{deliveryId}/deliver")
    @Transactional
    public ResponseEntity<DeliveryResponse> deliver(@PathVariable Long deliveryId) {
        logger.info("Marking delivery {} as delivered", deliveryId);
        
        Delivery delivery = deliveryRepository.findById(deliveryId)
            .orElseThrow(() -> new DeliveryNotFoundException(deliveryId));
        
        try {
            delivery.deliver();
            deliveryRepository.save(delivery);
            
            // Publish DeliveryDelivered event
            DeliveryDelivered event = new DeliveryDelivered(
                delivery.getId(),
                delivery.getOrderId(),
                delivery.getCourierId(),
                delivery.getDeliveryTime()
            );
            domainEventPublisher.publishDeliveryEvent(delivery.getId(), event);
            
            logger.info("Marked delivery {} as delivered at {}", deliveryId, delivery.getDeliveryTime());
            
            return ResponseEntity.ok(new DeliveryResponse(delivery));
        } catch (IllegalStateException e) {
            logger.error("Failed to mark delivery {} as delivered: {}", deliveryId, e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }
    
    /**
     * Exception thrown when a delivery is not found.
     */
    public static class DeliveryNotFoundException extends RuntimeException {
        public DeliveryNotFoundException(Long deliveryId) {
            super("Delivery not found: " + deliveryId);
        }
    }
    
    /**
     * Exception handler for DeliveryNotFoundException.
     */
    @ExceptionHandler(DeliveryNotFoundException.class)
    public ResponseEntity<String> handleDeliveryNotFound(DeliveryNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
    }
}
