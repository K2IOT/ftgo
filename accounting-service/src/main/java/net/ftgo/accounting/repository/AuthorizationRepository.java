package net.ftgo.accounting.repository;

import net.ftgo.accounting.domain.Authorization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for Authorization entity persistence.
 */
@Repository
public interface AuthorizationRepository extends JpaRepository<Authorization, Long> {
    
    /**
     * Finds an authorization by request ID (idempotency key).
     * 
     * @param requestId the request ID to search for
     * @return an Optional containing the authorization if found, empty otherwise
     */
    Optional<Authorization> findByRequestId(String requestId);
    
    /**
     * Checks if an authorization exists with the given request ID.
     * 
     * @param requestId the request ID to check
     * @return true if an authorization exists with the request ID, false otherwise
     */
    boolean existsByRequestId(String requestId);
}
