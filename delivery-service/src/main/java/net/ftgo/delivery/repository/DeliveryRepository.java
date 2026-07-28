package net.ftgo.delivery.repository;

import jakarta.persistence.LockModeType;
import net.ftgo.delivery.domain.Delivery;
import net.ftgo.delivery.domain.DeliveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** Repository for Delivery aggregate. */
@Repository
public interface DeliveryRepository extends JpaRepository<Delivery, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Delivery d where d.id = :id")
    Optional<Delivery> findByIdForUpdate(@Param("id") Long id);

    Optional<Delivery> findByOrderId(Long orderId);

    List<Delivery> findByCourierId(Long courierId);

    List<Delivery> findByStatus(DeliveryStatus status);

    List<Delivery> findByCourierIdAndStatus(Long courierId, DeliveryStatus status);
}
