package net.ftgo.delivery;

import net.ftgo.common.Address;
import net.ftgo.delivery.domain.Delivery;
import net.ftgo.delivery.domain.DeliveryStatus;
import net.ftgo.delivery.repository.DeliveryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Delivery Service.
 * Tests delivery repository and domain logic integration.
 */
@SpringBootTest
@ActiveProfiles("test")
class DeliveryServiceIntegrationTest {

    @MockitoBean
    private KafkaTemplate<Object, Object> kafkaTemplate;

    @Autowired
    private DeliveryRepository deliveryRepository;

    @BeforeEach
    void setUp() {
        deliveryRepository.deleteAll();
    }

    @Test
    void testCreateAndSaveDelivery() {
        // Given
        Long orderId = 1L;
        Address pickupAddress = new Address("123 Restaurant St", "San Francisco", "CA", "94102");
        Address deliveryAddress = new Address("456 Consumer Ave", "San Francisco", "CA", "94103");
        LocalDateTime scheduledTime = LocalDateTime.now().plusHours(1);

        Delivery delivery = new Delivery(orderId, pickupAddress, deliveryAddress, scheduledTime);

        // When
        Delivery saved = deliveryRepository.save(delivery);

        // Then
        assertNotNull(saved.getId());
        assertEquals(orderId, saved.getOrderId());
        assertEquals(DeliveryStatus.PENDING, saved.getStatus());

        // Verify can be retrieved
        Delivery retrieved = deliveryRepository.findById(saved.getId()).orElseThrow();
        assertEquals(saved.getId(), retrieved.getId());
        assertEquals(orderId, retrieved.getOrderId());
    }

    @Test
    void testFindByOrderId() {
        // Given
        Long orderId = 2L;
        Address pickupAddress = new Address("123 Restaurant St", "San Francisco", "CA", "94102");
        Address deliveryAddress = new Address("456 Consumer Ave", "San Francisco", "CA", "94103");
        LocalDateTime scheduledTime = LocalDateTime.now().plusHours(1);

        Delivery delivery = new Delivery(orderId, pickupAddress, deliveryAddress, scheduledTime);
        deliveryRepository.save(delivery);

        // When
        Delivery found = deliveryRepository.findByOrderId(orderId).orElseThrow();

        // Then
        assertEquals(orderId, found.getOrderId());
        assertEquals(DeliveryStatus.PENDING, found.getStatus());
    }

    @Test
    void testDeliveryStatusTransitions() {
        // Given
        Long orderId = 3L;
        Long courierId = 100L;
        Address pickupAddress = new Address("123 Restaurant St", "San Francisco", "CA", "94102");
        Address deliveryAddress = new Address("456 Consumer Ave", "San Francisco", "CA", "94103");
        LocalDateTime scheduledTime = LocalDateTime.now().plusHours(1);

        Delivery delivery = new Delivery(orderId, pickupAddress, deliveryAddress, scheduledTime);
        delivery = deliveryRepository.save(delivery);

        // When - assign courier
        delivery.assignCourier(courierId);
        delivery = deliveryRepository.save(delivery);

        // Then
        assertEquals(DeliveryStatus.ASSIGNED, delivery.getStatus());
        assertEquals(courierId, delivery.getCourierId());

        // When - pickup
        delivery.pickup();
        delivery = deliveryRepository.save(delivery);

        // Then
        assertEquals(DeliveryStatus.PICKED_UP, delivery.getStatus());
        assertNotNull(delivery.getPickupTime());

        // When - deliver
        delivery.deliver();
        delivery = deliveryRepository.save(delivery);

        // Then
        assertEquals(DeliveryStatus.DELIVERED, delivery.getStatus());
        assertNotNull(delivery.getDeliveryTime());
        assertTrue(delivery.getDeliveryTime().isAfter(delivery.getPickupTime()));
    }

    @Test
    void testFindByStatus() {
        // Given
        Address pickupAddress = new Address("123 Restaurant St", "San Francisco", "CA", "94102");
        Address deliveryAddress = new Address("456 Consumer Ave", "San Francisco", "CA", "94103");
        LocalDateTime scheduledTime = LocalDateTime.now().plusHours(1);

        Delivery delivery1 = new Delivery(10L, pickupAddress, deliveryAddress, scheduledTime);
        Delivery delivery2 = new Delivery(11L, pickupAddress, deliveryAddress, scheduledTime);
        delivery2.assignCourier(100L);

        deliveryRepository.save(delivery1);
        deliveryRepository.save(delivery2);

        // When
        var pendingDeliveries = deliveryRepository.findByStatus(DeliveryStatus.PENDING);
        var assignedDeliveries = deliveryRepository.findByStatus(DeliveryStatus.ASSIGNED);

        // Then
        assertEquals(1, pendingDeliveries.size());
        assertEquals(1, assignedDeliveries.size());
        assertEquals(10L, pendingDeliveries.get(0).getOrderId());
        assertEquals(11L, assignedDeliveries.get(0).getOrderId());
    }
}
