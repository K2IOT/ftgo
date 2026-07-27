package net.ftgo.delivery.api;

import net.ftgo.delivery.domain.Delivery;
import net.ftgo.delivery.service.DeliveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST API controller for authorized delivery operations. */
@RestController
@RequestMapping("/deliveries")
public class DeliveryController {

    private static final Logger logger = LoggerFactory.getLogger(DeliveryController.class);

    private final DeliveryService deliveryService;

    public DeliveryController(DeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    @GetMapping("/{deliveryId}")
    public ResponseEntity<DeliveryResponse> getDelivery(
        @PathVariable Long deliveryId,
        Authentication authentication
    ) {
        Delivery delivery = deliveryService.getDelivery(deliveryId, authentication);
        return ResponseEntity.ok(new DeliveryResponse(delivery));
    }

    @PostMapping("/{deliveryId}/assign")
    public ResponseEntity<DeliveryResponse> assignCourier(
        @PathVariable Long deliveryId,
        Authentication authentication
    ) {
        logger.info("Authenticated courier is claiming delivery {}", deliveryId);
        try {
            Delivery delivery = deliveryService.claimDelivery(deliveryId, authentication);
            return ResponseEntity.ok(new DeliveryResponse(delivery));
        } catch (AccessDeniedException denied) {
            throw denied;
        } catch (IllegalStateException conflict) {
            logger.warn("Delivery {} claim conflict: {}", deliveryId, conflict.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    @PostMapping("/{deliveryId}/pickup")
    public ResponseEntity<DeliveryResponse> pickup(
        @PathVariable Long deliveryId,
        Authentication authentication
    ) {
        logger.info("Authenticated courier is picking up delivery {}", deliveryId);
        try {
            Delivery delivery = deliveryService.pickup(deliveryId, authentication);
            return ResponseEntity.ok(new DeliveryResponse(delivery));
        } catch (AccessDeniedException denied) {
            throw denied;
        } catch (IllegalStateException conflict) {
            logger.warn("Delivery {} pickup conflict: {}", deliveryId, conflict.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    @PostMapping("/{deliveryId}/deliver")
    public ResponseEntity<DeliveryResponse> deliver(
        @PathVariable Long deliveryId,
        Authentication authentication
    ) {
        logger.info("Authenticated courier is delivering delivery {}", deliveryId);
        try {
            Delivery delivery = deliveryService.deliver(deliveryId, authentication);
            return ResponseEntity.ok(new DeliveryResponse(delivery));
        } catch (AccessDeniedException denied) {
            throw denied;
        } catch (IllegalStateException conflict) {
            logger.warn("Delivery {} completion conflict: {}", deliveryId, conflict.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleDeliveryNotFound(IllegalArgumentException error) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error.getMessage());
    }
}
