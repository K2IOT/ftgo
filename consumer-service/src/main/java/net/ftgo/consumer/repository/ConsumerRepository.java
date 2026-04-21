package net.ftgo.consumer.repository;

import net.ftgo.consumer.domain.Consumer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for Consumer aggregate persistence.
 */
@Repository
public interface ConsumerRepository extends JpaRepository<Consumer, Long> {
    
    /**
     * Finds a consumer by email address.
     * 
     * @param email the email address to search for
     * @return an Optional containing the consumer if found, empty otherwise
     */
    Optional<Consumer> findByEmail(String email);
    
    /**
     * Checks if a consumer exists with the given email address.
     * 
     * @param email the email address to check
     * @return true if a consumer exists with the email, false otherwise
     */
    boolean existsByEmail(String email);
}
