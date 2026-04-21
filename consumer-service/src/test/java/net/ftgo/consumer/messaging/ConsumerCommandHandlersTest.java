package net.ftgo.consumer.messaging;

import net.ftgo.common.Money;
import net.ftgo.consumer.domain.Consumer;
import net.ftgo.consumer.repository.ConsumerRepository;
import net.ftgo.consumer.service.ConsumerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Consumer Service command handling logic.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ConsumerCommandHandlersTest {
    
    @Autowired
    private ConsumerRepository consumerRepository;
    
    @Autowired
    private ConsumerService consumerService;
    
    @BeforeEach
    void setUp() {
        consumerRepository.deleteAll();
    }
    
    @Test
    void testVerifyConsumerWithSufficientCredit() {
        // Create consumer with $100 credit limit
        Consumer consumer = new Consumer("Test User", "test@example.com", new Money("100.00"));
        consumer = consumerRepository.save(consumer);
        
        // Verify consumer for $50 order
        boolean verified = consumerService.verifyConsumerCredit(
            consumer.getId(),
            new Money(new BigDecimal("50.00"))
        );
        
        assertTrue(verified, "Consumer should be verified with sufficient credit");
    }
    
    @Test
    void testVerifyConsumerWithInsufficientCredit() {
        // Create consumer with $50 credit limit
        Consumer consumer = new Consumer("Test User", "test@example.com", new Money("50.00"));
        consumer = consumerRepository.save(consumer);
        
        // Verify consumer for $100 order
        boolean verified = consumerService.verifyConsumerCredit(
            consumer.getId(),
            new Money(new BigDecimal("100.00"))
        );
        
        assertFalse(verified, "Consumer should not be verified with insufficient credit");
    }
    
    @Test
    void testVerifyConsumerNotFound() {
        // Verify non-existent consumer
        boolean verified = consumerService.verifyConsumerCredit(
            999L,
            new Money(new BigDecimal("50.00"))
        );
        
        assertFalse(verified, "Non-existent consumer should not be verified");
    }
    
    @Test
    void testVerifyConsumerWithExactCredit() {
        // Create consumer with $100 credit limit
        Consumer consumer = new Consumer("Test User", "test@example.com", new Money("100.00"));
        consumer = consumerRepository.save(consumer);
        
        // Verify consumer for exactly $100 order
        boolean verified = consumerService.verifyConsumerCredit(
            consumer.getId(),
            new Money(new BigDecimal("100.00"))
        );
        
        assertTrue(verified, "Consumer should be verified with exact credit match");
    }
}
