package net.ftgo.consumer.repository;

import net.ftgo.common.Money;
import net.ftgo.consumer.domain.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for ConsumerRepository.
 */
@DataJpaTest
@ActiveProfiles("test")
class ConsumerRepositoryTest {
    
    @Autowired
    private TestEntityManager entityManager;
    
    @Autowired
    private ConsumerRepository consumerRepository;
    
    @Test
    void testSaveAndFindById() {
        Money creditLimit = new Money(new BigDecimal("1000.00"));
        Consumer consumer = new Consumer("John Doe", "john@example.com", creditLimit);
        
        Consumer saved = consumerRepository.save(consumer);
        entityManager.flush();
        
        assertNotNull(saved.getId());
        
        Optional<Consumer> found = consumerRepository.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals("John Doe", found.get().getName());
        assertEquals("john@example.com", found.get().getEmail());
        assertEquals(creditLimit, found.get().getCreditLimit());
    }
    
    @Test
    void testFindByEmail() {
        Money creditLimit = new Money(new BigDecimal("1000.00"));
        Consumer consumer = new Consumer("John Doe", "john@example.com", creditLimit);
        
        consumerRepository.save(consumer);
        entityManager.flush();
        
        Optional<Consumer> found = consumerRepository.findByEmail("john@example.com");
        assertTrue(found.isPresent());
        assertEquals("John Doe", found.get().getName());
    }
    
    @Test
    void testFindByEmailNotFound() {
        Optional<Consumer> found = consumerRepository.findByEmail("nonexistent@example.com");
        assertFalse(found.isPresent());
    }
    
    @Test
    void testExistsByEmail() {
        Money creditLimit = new Money(new BigDecimal("1000.00"));
        Consumer consumer = new Consumer("John Doe", "john@example.com", creditLimit);
        
        consumerRepository.save(consumer);
        entityManager.flush();
        
        assertTrue(consumerRepository.existsByEmail("john@example.com"));
        assertFalse(consumerRepository.existsByEmail("nonexistent@example.com"));
    }
    
    @Test
    void testEmailUniqueness() {
        Money creditLimit = new Money(new BigDecimal("1000.00"));
        Consumer consumer1 = new Consumer("John Doe", "john@example.com", creditLimit);
        Consumer consumer2 = new Consumer("Jane Doe", "john@example.com", creditLimit);
        
        consumerRepository.save(consumer1);
        entityManager.flush();
        
        // Attempting to save consumer2 with duplicate email should fail
        assertThrows(Exception.class, () -> {
            consumerRepository.save(consumer2);
            entityManager.flush();
        });
    }
    
    @Test
    void testUpdateConsumer() {
        Money creditLimit = new Money(new BigDecimal("1000.00"));
        Consumer consumer = new Consumer("John Doe", "john@example.com", creditLimit);
        
        Consumer saved = consumerRepository.save(consumer);
        entityManager.flush();
        
        // Update profile
        saved.updateProfile("John Smith", "john.smith@example.com");
        consumerRepository.save(saved);
        entityManager.flush();
        
        Optional<Consumer> updated = consumerRepository.findById(saved.getId());
        assertTrue(updated.isPresent());
        assertEquals("John Smith", updated.get().getName());
        assertEquals("john.smith@example.com", updated.get().getEmail());
    }
    
    @Test
    void testReserveCreditPersistence() {
        Money creditLimit = new Money(new BigDecimal("1000.00"));
        Consumer consumer = new Consumer("John Doe", "john@example.com", creditLimit);
        
        Consumer saved = consumerRepository.save(consumer);
        entityManager.flush();
        
        // Reserve credit
        saved.reserveCredit(new Money(new BigDecimal("300.00")));
        consumerRepository.save(saved);
        entityManager.flush();
        entityManager.clear(); // Clear persistence context
        
        // Reload from database
        Optional<Consumer> reloaded = consumerRepository.findById(saved.getId());
        assertTrue(reloaded.isPresent());
        assertEquals(new Money(new BigDecimal("700.00")), reloaded.get().getAvailableCredit());
    }
}
