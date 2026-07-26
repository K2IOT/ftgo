package net.ftgo.accounting.repository;

import jakarta.persistence.LockModeType;
import net.ftgo.accounting.domain.Authorization;
import net.ftgo.accounting.domain.AuthorizationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AuthorizationRepository extends JpaRepository<Authorization, Long> {

    Optional<Authorization> findByRequestId(String requestId);

    boolean existsByRequestId(String requestId);

    Optional<Authorization> findByProviderAuthorizationId(String providerAuthorizationId);

    List<Authorization> findTop100ByStatusInOrderByCreatedAtAsc(
        List<AuthorizationStatus> statuses
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select authorization from Authorization authorization "
        + "where authorization.providerAuthorizationId = :providerAuthorizationId")
    Optional<Authorization> findByProviderAuthorizationIdForUpdate(
        @Param("providerAuthorizationId") String providerAuthorizationId
    );
}
