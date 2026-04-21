package net.ftgo.consumer.service;

import net.ftgo.common.Money;
import net.ftgo.consumer.domain.Consumer;
import net.ftgo.consumer.domain.ConsumerUpdated;
import net.ftgo.consumer.messaging.DomainEventPublisher;
import net.ftgo.consumer.repository.ConsumerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing Consumer operations.
 */
@Service
@Transactional
public class ConsumerService {
    
    private final ConsumerRepository consumerRepository;
    private final DomainEventPublisher eventPublisher;
    
    public ConsumerService(ConsumerRepository consumerRepository, 
                          DomainEventPublisher eventPublisher) {
        this.consumerRepository = consumerRepository;
        this.eventPublisher = eventPublisher;
    }
    
    /**
     * Creates a new consumer account.
     * 
     * @param name the consumer's name
     * @param email the consumer's email
     * @param creditLimit the consumer's credit limit
     * @return the created consumer
     * @throws IllegalArgumentException if email already exists
     */
    public Consumer createConsumer(String name, String email, Money creditLimit) {
        if (consumerRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Consumer with email " + email + " already exists");
        }
        
        Consumer consumer = new Consumer(name, email, creditLimit);
        return consumerRepository.save(consumer);
    }
    
    /**
     * Verifies if a consumer exists and has sufficient credit for an order.
     * 
     * @param consumerId the consumer ID
     * @param orderTotal the order total amount
     * @return true if consumer exists and has sufficient credit
     */
    @Transactional(readOnly = true)
    public boolean verifyConsumerCredit(Long consumerId, Money orderTotal) {
        return consumerRepository.findById(consumerId)
            .map(consumer -> consumer.hasAvailableCredit(orderTotal))
            .orElse(false);
    }
    
    /**
     * Finds a consumer by ID.
     * 
     * @param consumerId the consumer ID
     * @return the consumer
     * @throws IllegalArgumentException if consumer not found
     */
    @Transactional(readOnly = true)
    public Consumer findConsumer(Long consumerId) {
        return consumerRepository.findById(consumerId)
            .orElseThrow(() -> new IllegalArgumentException("Consumer not found: " + consumerId));
    }
    
    /**
     * Updates consumer profile information and publishes ConsumerUpdated event.
     * 
     * @param consumerId the consumer ID
     * @param name the new name (optional)
     * @param email the new email (optional)
     * @return the updated consumer
     * @throws IllegalArgumentException if consumer not found
     */
    public Consumer updateConsumer(Long consumerId, String name, String email) {
        Consumer consumer = consumerRepository.findById(consumerId)
            .orElseThrow(() -> new IllegalArgumentException("Consumer not found: " + consumerId));
        
        // Update profile
        consumer.updateProfile(name, email);
        consumer = consumerRepository.save(consumer);
        
        // Publish ConsumerUpdated event via Transactional Outbox
        ConsumerUpdated event = new ConsumerUpdated(
            consumer.getId(),
            consumer.getName(),
            consumer.getEmail(),
            consumer.getCreditLimit().getAmount(),
            consumer.getAvailableCredit().getAmount(),
            consumer.getUpdatedAt()
        );
        eventPublisher.publishConsumerEvent(consumer.getId(), event);
        
        return consumer;
    }
}
