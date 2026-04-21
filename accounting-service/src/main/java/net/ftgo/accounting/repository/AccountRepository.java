package net.ftgo.accounting.repository;

import net.ftgo.accounting.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for Account aggregate persistence.
 */
@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {
    
    /**
     * Finds an account by consumer ID.
     * 
     * @param consumerId the consumer ID to search for
     * @return an Optional containing the account if found, empty otherwise
     */
    Optional<Account> findByConsumerId(Long consumerId);
    
    /**
     * Checks if an account exists for the given consumer ID.
     * 
     * @param consumerId the consumer ID to check
     * @return true if an account exists for the consumer, false otherwise
     */
    boolean existsByConsumerId(Long consumerId);
}
