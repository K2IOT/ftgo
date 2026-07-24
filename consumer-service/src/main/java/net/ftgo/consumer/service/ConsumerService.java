package net.ftgo.consumer.service;

import net.ftgo.common.Money;
import net.ftgo.consumer.domain.Consumer;
import net.ftgo.consumer.domain.ConsumerUpdated;
import net.ftgo.consumer.messaging.DomainEventPublisher;
import net.ftgo.consumer.repository.ConsumerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service for managing Consumer operations. */
@Service
@Transactional
public class ConsumerService {

    private final ConsumerRepository consumerRepository;
    private final DomainEventPublisher eventPublisher;

    public ConsumerService(
        ConsumerRepository consumerRepository,
        DomainEventPublisher eventPublisher
    ) {
        this.consumerRepository = consumerRepository;
        this.eventPublisher = eventPublisher;
    }

    public Consumer createConsumer(String name, String email, Money creditLimit) {
        if (consumerRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Consumer with email " + email + " already exists");
        }
        return consumerRepository.save(new Consumer(name, email, creditLimit));
    }

    @Transactional(readOnly = true)
    public boolean verifyConsumerCredit(Long consumerId, Money orderTotal) {
        return consumerRepository.findById(consumerId)
            .map(consumer -> consumer.hasAvailableCredit(orderTotal))
            .orElse(false);
    }

    @Transactional(readOnly = true)
    public Consumer findConsumer(Long consumerId) {
        return consumerRepository.findById(consumerId)
            .orElseThrow(() -> new IllegalArgumentException("Consumer not found: " + consumerId));
    }

    public Consumer updateConsumer(Long consumerId, String name, String email) {
        Consumer consumer = consumerRepository.findById(consumerId)
            .orElseThrow(() -> new IllegalArgumentException("Consumer not found: " + consumerId));

        consumer.updateProfile(name, email);
        consumer = consumerRepository.saveAndFlush(consumer);

        ConsumerUpdated event = new ConsumerUpdated(
            consumer.getId(),
            consumer.getName(),
            consumer.getEmail(),
            consumer.getCreditLimit().getAmount(),
            consumer.getAvailableCredit().getAmount(),
            consumer.getUpdatedAt()
        );
        eventPublisher.publishConsumerEvent(
            consumer.getId(),
            consumer.getVersion(),
            event
        );
        return consumer;
    }
}
