package net.ftgo.kitchen.repository;

import jakarta.persistence.LockModeType;
import net.ftgo.kitchen.domain.Ticket;
import net.ftgo.kitchen.domain.TicketState;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {

    Optional<Ticket> findByOrderId(Long orderId);

    List<Ticket> findByRestaurantIdAndState(Long restaurantId, TicketState state);

    List<Ticket> findByRestaurantId(Long restaurantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Ticket t where t.id = :ticketId")
    Optional<Ticket> findByIdForUpdate(@Param("ticketId") Long ticketId);

    @Query("select t.id from Ticket t "
        + "where t.state = net.ftgo.kitchen.domain.TicketState.AWAITING_ACCEPTANCE "
        + "and t.acceptanceDeadline <= :now "
        + "order by t.acceptanceDeadline, t.id")
    List<Long> findDueAcceptanceTimeoutIds(
        @Param("now") LocalDateTime now,
        Pageable pageable
    );
}
