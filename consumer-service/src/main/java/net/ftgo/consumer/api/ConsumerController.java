package net.ftgo.consumer.api;

import jakarta.validation.Valid;
import net.ftgo.common.Money;
import net.ftgo.consumer.domain.Consumer;
import net.ftgo.consumer.service.ConsumerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST API controller for Consumer operations.
 * 
 * Provides endpoints for:
 * - POST /consumers - Create new consumer account
 * - GET /consumers/{id} - Get consumer details
 * - PUT /consumers/{id} - Update consumer profile
 */
@RestController
@RequestMapping("/consumers")
public class ConsumerController {
    
    private static final Logger logger = LoggerFactory.getLogger(ConsumerController.class);
    
    private final ConsumerService consumerService;
    
    public ConsumerController(ConsumerService consumerService) {
        this.consumerService = consumerService;
    }
    
    /**
     * Creates a new consumer account.
     * 
     * @param request the consumer creation request
     * @return the created consumer
     */
    @PostMapping
    public ResponseEntity<ConsumerResponse> createConsumer(@Valid @RequestBody CreateConsumerRequest request) {
        logger.info("Creating consumer with email: {}", request.getEmail());
        
        Money creditLimit = new Money(request.getCreditLimit());
        Consumer consumer = consumerService.createConsumer(
            request.getName(),
            request.getEmail(),
            creditLimit
        );
        
        logger.info("Consumer created with ID: {}", consumer.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(new ConsumerResponse(consumer));
    }
    
    /**
     * Gets consumer details by ID.
     * 
     * @param id the consumer ID
     * @return the consumer details
     */
    @GetMapping("/{id}")
    public ResponseEntity<ConsumerResponse> getConsumer(@PathVariable Long id) {
        logger.debug("Fetching consumer with ID: {}", id);
        
        Consumer consumer = consumerService.findConsumer(id);
        return ResponseEntity.ok(new ConsumerResponse(consumer));
    }
    
    /**
     * Updates consumer profile information.
     * 
     * @param id the consumer ID
     * @param request the update request
     * @return the updated consumer
     */
    @PutMapping("/{id}")
    public ResponseEntity<ConsumerResponse> updateConsumer(
            @PathVariable Long id,
            @Valid @RequestBody UpdateConsumerRequest request) {
        logger.info("Updating consumer with ID: {}", id);
        
        Consumer consumer = consumerService.updateConsumer(id, request.getName(), request.getEmail());
        
        logger.info("Consumer updated with ID: {}", consumer.getId());
        return ResponseEntity.ok(new ConsumerResponse(consumer));
    }
    
    /**
     * Exception handler for IllegalArgumentException.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex) {
        logger.error("Validation error: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(new ErrorResponse(ex.getMessage()));
    }
    
    /**
     * Error response DTO.
     */
    public static class ErrorResponse {
        private final String error;
        
        public ErrorResponse(String error) {
            this.error = error;
        }
        
        public String getError() {
            return error;
        }
    }
}
