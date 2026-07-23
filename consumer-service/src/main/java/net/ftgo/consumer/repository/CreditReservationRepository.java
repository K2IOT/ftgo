package net.ftgo.consumer.repository;

import jakarta.persistence.LockModeType;
import net.ftgo.consumer.domain.CreditReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CreditReservationRepository extends JpaRepository<CreditReservation, Long> {

    Optional<CreditReservation> findByOrderId(Long orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from CreditReservation r where r.orderId = :orderId")
    Optional<CreditReservation> findByOrderIdForUpdate(@Param("orderId") Long orderId);
}
