package net.ftgo.consumer.repository;

import jakarta.persistence.LockModeType;
import net.ftgo.consumer.domain.Consumer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ConsumerRepository extends JpaRepository<Consumer, Long> {

    Optional<Consumer> findByEmail(String email);

    boolean existsByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Consumer c where c.id = :consumerId")
    Optional<Consumer> findByIdForUpdate(@Param("consumerId") Long consumerId);
}
