package net.ftgo.accounting.repository;

import net.ftgo.accounting.domain.Account;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    @Override
    @EntityGraph(attributePaths = "authorizations")
    Optional<Account> findById(Long id);

    @EntityGraph(attributePaths = "authorizations")
    Optional<Account> findByConsumerId(Long consumerId);

    @Query("select distinct a from Account a join fetch a.authorizations auth where auth.id = :authorizationId")
    Optional<Account> findByAuthorizationId(@Param("authorizationId") Long authorizationId);

    boolean existsByConsumerId(Long consumerId);
}
