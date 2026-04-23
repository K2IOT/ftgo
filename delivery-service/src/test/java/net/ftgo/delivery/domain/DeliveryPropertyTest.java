package net.ftgo.delivery.domain;

import net.ftgo.common.Address;
import net.jqwik.api.*;

import java.time.LocalDateTime;

/**
 * Property-based tests for Delivery aggregate.
 * 
 * **Property 5: Delivery Temporal Ordering**
 * **Validates: Requirements 8.6**
 * 
 * Tests that pickupTime < deliveryTime for all completed deliveries.
 */
class DeliveryPropertyTest {
    
    /**
     * Property 5: Delivery Temporal Ordering
     * 
     * For all completed deliveries, the pickup time must occur before the delivery time.
     * This ensures temporal consistency in the delivery lifecycle.
     * 
     * **Validates: Requirements 8.6**
     */
    @Property(tries = 100)
    void deliveryTemporalOrdering(
            @ForAll("validOrderId") Long orderId,
            @ForAll("validAddress") Address pickupAddress,
            @ForAll("validAddress") Address deliveryAddress,
            @ForAll("futureScheduledTime") LocalDateTime scheduledTime,
            @ForAll("validCourierId") Long courierId) throws InterruptedException {
        
        // Given: A delivery that goes through the complete lifecycle
        Delivery delivery = new Delivery(orderId, pickupAddress, deliveryAddress, scheduledTime);
        
        // When: Delivery is assigned, picked up, and delivered
        delivery.assignCourier(courierId);
        delivery.pickup();
        
        // Small delay to ensure deliveryTime > pickupTime
        Thread.sleep(10);
        
        delivery.deliver();
        
        // Then: Pickup time must be before delivery time (temporal ordering)
        LocalDateTime pickupTime = delivery.getPickupTime();
        LocalDateTime deliveryTime = delivery.getDeliveryTime();
        
        assert pickupTime != null : "Pickup time must not be null for completed delivery";
        assert deliveryTime != null : "Delivery time must not be null for completed delivery";
        assert pickupTime.isBefore(deliveryTime) : 
            String.format("Pickup time (%s) must be before delivery time (%s)", pickupTime, deliveryTime);
    }
    
    /**
     * Provides valid order IDs (positive longs).
     */
    @Provide
    Arbitrary<Long> validOrderId() {
        return Arbitraries.longs().between(1L, 1000000L);
    }
    
    /**
     * Provides valid courier IDs (positive longs).
     */
    @Provide
    Arbitrary<Long> validCourierId() {
        return Arbitraries.longs().between(1L, 10000L);
    }
    
    /**
     * Provides valid addresses with realistic US city/state combinations.
     */
    @Provide
    Arbitrary<Address> validAddress() {
        Arbitrary<String> streets = Arbitraries.strings()
            .numeric()
            .ofMinLength(1)
            .ofMaxLength(5)
            .map(num -> num + " Main St");
        
        Arbitrary<String> cities = Arbitraries.of(
            "San Francisco", "Oakland", "San Jose", "Berkeley", "Palo Alto"
        );
        
        Arbitrary<String> states = Arbitraries.of("CA");
        
        Arbitrary<String> zipCodes = Arbitraries.strings()
            .numeric()
            .ofLength(5);
        
        return Combinators.combine(streets, cities, states, zipCodes)
            .as(Address::new);
    }
    
    /**
     * Provides future scheduled times (1-24 hours from now).
     */
    @Provide
    Arbitrary<LocalDateTime> futureScheduledTime() {
        return Arbitraries.integers().between(1, 24)
            .map(hours -> LocalDateTime.now().plusHours(hours));
    }
}
