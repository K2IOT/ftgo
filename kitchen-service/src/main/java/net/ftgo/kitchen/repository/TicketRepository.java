package net.ftgo.kitchen.repository;

import net.ftgo.kitchen.domain.Ticket;
import net.ftgo.kitchen.domain.TicketState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Ticket aggregate.
 */
@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {
    
    /**
     * Finds a ticket by order ID.
     * 
     * @param orderId the order ID
     * @return the ticket if found
     */
    Optional<Ticket> findByOrderId(Long orderId);
    
    /**
     * Finds all tickets for a restaurant with a specific state.
     * 
     * @param restaurantId the restaurant ID
     * @param state the ticket state
     * @return list of tickets
     */
    List<Ticket> findByRestaurantIdAndState(Long restaurantId, TicketState state);
    
    /**
     * Finds all tickets for a restaurant.
     * 
     * @param restaurantId the restaurant ID
     * @return list of tickets
     */
    List<Ticket> findByRestaurantId(Long restaurantId);
}
