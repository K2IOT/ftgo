package net.ftgo.delivery.domain;

import net.ftgo.common.Address;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Delivery aggregate.
 * Tests delivery status transitions and estimated delivery time calculation.
 */
class DeliveryTest {
    
    private static final Long ORDER_ID = 1L;
    private static final Long COURIER_ID = 100L;
    private static final Address PICKUP_ADDRESS = new Address(
        "123 Restaurant St", "San Francisco", "CA", "94102"
    );
    private static final Address DELIVERY_ADDRESS = new Address(
        "456 Consumer Ave", "San Francisco", "CA", "94103"
    );
    private static final LocalDateTime SCHEDULED_TIME = LocalDateTime.now().plusHours(1);
    
    @Test
    void testCreateDelivery() {
        Delivery delivery = new Delivery(ORDER_ID, PICKUP_ADDRESS, DELIVERY_ADDRESS, SCHEDULED_TIME);
        
        assertEquals(ORDER_ID, delivery.getOrderId());
        assertEquals(PICKUP_ADDRESS, delivery.getPickupAddress());
        assertEquals(DELIVERY_ADDRESS, delivery.getDeliveryAddress());
        assertEquals(SCHEDULED_TIME, delivery.getScheduledTime());
        assertEquals(DeliveryStatus.PENDING, delivery.getStatus());
        assertNull(delivery.getCourierId());
        assertNull(delivery.getPickupTime());
        assertNull(delivery.getDeliveryTime());
    }
    
    @Test
    void testCreateDeliveryWithNullOrderId() {
        assertThrows(IllegalArgumentException.class, () -> 
            new Delivery(null, PICKUP_ADDRESS, DELIVERY_ADDRESS, SCHEDULED_TIME)
        );
    }
    
    @Test
    void testCreateDeliveryWithNullPickupAddress() {
        assertThrows(IllegalArgumentException.class, () -> 
            new Delivery(ORDER_ID, null, DELIVERY_ADDRESS, SCHEDULED_TIME)
        );
    }
    
    @Test
    void testCreateDeliveryWithNullDeliveryAddress() {
        assertThrows(IllegalArgumentException.class, () -> 
            new Delivery(ORDER_ID, PICKUP_ADDRESS, null, SCHEDULED_TIME)
        );
    }
    
    @Test
    void testCreateDeliveryWithNullScheduledTime() {
        assertThrows(IllegalArgumentException.class, () -> 
            new Delivery(ORDER_ID, PICKUP_ADDRESS, DELIVERY_ADDRESS, null)
        );
    }
    
    @Test
    void testAssignCourier() {
        Delivery delivery = new Delivery(ORDER_ID, PICKUP_ADDRESS, DELIVERY_ADDRESS, SCHEDULED_TIME);
        
        delivery.assignCourier(COURIER_ID);
        
        assertEquals(COURIER_ID, delivery.getCourierId());
        assertEquals(DeliveryStatus.ASSIGNED, delivery.getStatus());
    }
    
    @Test
    void testAssignCourierWithNullCourierId() {
        Delivery delivery = new Delivery(ORDER_ID, PICKUP_ADDRESS, DELIVERY_ADDRESS, SCHEDULED_TIME);
        
        assertThrows(IllegalArgumentException.class, () -> 
            delivery.assignCourier(null)
        );
    }
    
    @Test
    void testAssignCourierWhenNotPending() {
        Delivery delivery = new Delivery(ORDER_ID, PICKUP_ADDRESS, DELIVERY_ADDRESS, SCHEDULED_TIME);
        delivery.assignCourier(COURIER_ID);
        
        assertThrows(IllegalStateException.class, () -> 
            delivery.assignCourier(COURIER_ID + 1)
        );
    }
    
    @Test
    void testPickup() {
        Delivery delivery = new Delivery(ORDER_ID, PICKUP_ADDRESS, DELIVERY_ADDRESS, SCHEDULED_TIME);
        delivery.assignCourier(COURIER_ID);
        
        delivery.pickup();
        
        assertEquals(DeliveryStatus.PICKED_UP, delivery.getStatus());
        assertNotNull(delivery.getPickupTime());
    }
    
    @Test
    void testPickupWhenNotAssigned() {
        Delivery delivery = new Delivery(ORDER_ID, PICKUP_ADDRESS, DELIVERY_ADDRESS, SCHEDULED_TIME);
        
        assertThrows(IllegalStateException.class, delivery::pickup);
    }
    
    @Test
    void testDeliver() throws InterruptedException {
        Delivery delivery = new Delivery(ORDER_ID, PICKUP_ADDRESS, DELIVERY_ADDRESS, SCHEDULED_TIME);
        delivery.assignCourier(COURIER_ID);
        delivery.pickup();
        
        // Small delay to ensure deliveryTime > pickupTime
        Thread.sleep(10);
        
        delivery.deliver();
        
        assertEquals(DeliveryStatus.DELIVERED, delivery.getStatus());
        assertNotNull(delivery.getDeliveryTime());
        assertTrue(delivery.getDeliveryTime().isAfter(delivery.getPickupTime()));
    }
    
    @Test
    void testDeliverWhenNotPickedUp() {
        Delivery delivery = new Delivery(ORDER_ID, PICKUP_ADDRESS, DELIVERY_ADDRESS, SCHEDULED_TIME);
        delivery.assignCourier(COURIER_ID);
        
        assertThrows(IllegalStateException.class, delivery::deliver);
    }
    
    @Test
    void testStatusTransitionFlow() throws InterruptedException {
        Delivery delivery = new Delivery(ORDER_ID, PICKUP_ADDRESS, DELIVERY_ADDRESS, SCHEDULED_TIME);
        
        // PENDING -> ASSIGNED
        assertEquals(DeliveryStatus.PENDING, delivery.getStatus());
        delivery.assignCourier(COURIER_ID);
        assertEquals(DeliveryStatus.ASSIGNED, delivery.getStatus());
        
        // ASSIGNED -> PICKED_UP
        delivery.pickup();
        assertEquals(DeliveryStatus.PICKED_UP, delivery.getStatus());
        
        // Small delay to ensure deliveryTime > pickupTime
        Thread.sleep(10);
        
        // PICKED_UP -> DELIVERED
        delivery.deliver();
        assertEquals(DeliveryStatus.DELIVERED, delivery.getStatus());
    }
    
    @Test
    void testCalculateEstimatedDeliveryTimeSameCity() {
        // Same city delivery
        Address sameCity = new Address("789 Another St", "San Francisco", "CA", "94104");
        Delivery delivery = new Delivery(ORDER_ID, PICKUP_ADDRESS, sameCity, SCHEDULED_TIME);
        
        LocalDateTime estimated = delivery.calculateEstimatedDeliveryTime();
        
        // Same city: 30 minutes base + 5 minutes per 10 miles (5 miles) = 30 + 2.5 = ~32-33 minutes
        assertTrue(estimated.isAfter(SCHEDULED_TIME.plusMinutes(30)));
        assertTrue(estimated.isBefore(SCHEDULED_TIME.plusMinutes(40)));
    }
    
    @Test
    void testCalculateEstimatedDeliveryTimeDifferentCity() {
        // Different city delivery
        Address differentCity = new Address("789 Another St", "Oakland", "CA", "94601");
        Delivery delivery = new Delivery(ORDER_ID, PICKUP_ADDRESS, differentCity, SCHEDULED_TIME);
        
        LocalDateTime estimated = delivery.calculateEstimatedDeliveryTime();
        
        // Different city: 30 minutes base + 5 minutes per 10 miles (20 miles) = 30 + 10 = 40 minutes
        assertTrue(estimated.isAfter(SCHEDULED_TIME.plusMinutes(35)));
        assertTrue(estimated.isBefore(SCHEDULED_TIME.plusMinutes(45)));
    }
    
    @Test
    void testTemporalOrderingPickupBeforeDelivery() throws InterruptedException {
        Delivery delivery = new Delivery(ORDER_ID, PICKUP_ADDRESS, DELIVERY_ADDRESS, SCHEDULED_TIME);
        delivery.assignCourier(COURIER_ID);
        delivery.pickup();
        
        LocalDateTime pickupTime = delivery.getPickupTime();
        
        // Small delay to ensure deliveryTime > pickupTime
        Thread.sleep(10);
        
        delivery.deliver();
        
        LocalDateTime deliveryTime = delivery.getDeliveryTime();
        
        // Verify temporal ordering: pickupTime < deliveryTime
        assertTrue(pickupTime.isBefore(deliveryTime), 
            "Pickup time must be before delivery time");
    }
}
