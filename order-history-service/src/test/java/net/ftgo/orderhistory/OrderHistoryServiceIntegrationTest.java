package net.ftgo.orderhistory;

import net.ftgo.common.Money;
import net.ftgo.common.orderflow.events.OrderApproved;
import net.ftgo.common.orderflow.events.OrderCreated;
import net.ftgo.orderhistory.domain.OrderHistoryRecord;
import net.ftgo.orderhistory.domain.ProcessedMessage;
import net.ftgo.orderhistory.messaging.*;
import net.ftgo.orderhistory.repository.OrderHistoryRepository;
import net.ftgo.orderhistory.repository.ProcessedMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.CassandraContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.TimeUnit;

/**
 * Integration test for Order History Service with ScyllaDB.
 * 
 * Uses Testcontainers to spin up a real Cassandra instance for testing.
 * Tests the complete flow from event handling to database persistence.
 */
@SpringBootTest
@Testcontainers
class OrderHistoryServiceIntegrationTest {
    
    @Container
    static CassandraContainer<?> cassandra = new CassandraContainer<>("cassandra:4.1")
        .withExposedPorts(9042)
        .withInitScript("order-history-test-keyspace.cql");
    
    @DynamicPropertySource
    static void cassandraProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.cassandra.contact-points", cassandra::getHost);
        registry.add("spring.cassandra.port", cassandra::getFirstMappedPort);
        registry.add("spring.cassandra.local-datacenter", () -> "datacenter1");
        registry.add("spring.cassandra.keyspace-name", () -> "ftgo_order_history_test");
    }
    
    @Autowired
    private OrderHistoryEventHandlers eventHandlers;
    
    @Autowired
    private OrderHistoryRepository orderHistoryRepository;
    
    @Autowired
    private ProcessedMessageRepository processedMessageRepository;
    
    @BeforeEach
    void setUp() {
        // Clean up before each test
        orderHistoryRepository.deleteAll();
        processedMessageRepository.deleteAll();
    }
    
    @Test
    void testCompleteOrderLifecycleEventFlow() throws Exception {
        // Step 1: OrderCreated
        OrderCreated orderCreated = new OrderCreated();
        orderCreated.setOrderId(123L);
        orderCreated.setConsumerId(456L);
        orderCreated.setRestaurantId(789L);
        orderCreated.setStatus("APPROVAL_PENDING");
        orderCreated.setOrderTotal(new Money("45.99"));
        orderCreated.setDeliveryAddress("123 Main St");
        orderCreated.setDeliveryTime(LocalDateTime.now().plusHours(1));
        orderCreated.setCreatedAt(LocalDateTime.now());
        
        OrderCreated.LineItem lineItem = new OrderCreated.LineItem();
        lineItem.setMenuItemId(1L);
        lineItem.setName("Burger");
        lineItem.setPrice(new Money("12.99"));
        lineItem.setQuantity(2);
        orderCreated.setLineItems(Arrays.asList(lineItem));
        
        String payload1 = new com.fasterxml.jackson.databind.ObjectMapper()
            .findAndRegisterModules()
            .writeValueAsString(orderCreated);
        
        eventHandlers.handleOrderEvent(payload1, "Order#123", "OrderCreated");
        
        // Verify order created
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            Optional<OrderHistoryRecord> record = orderHistoryRepository.findById("123");
            assertTrue(record.isPresent());
            assertEquals("APPROVAL_PENDING", record.get().getStatus());
            assertEquals(456L, record.get().getConsumerId());
        });
        
        // Step 2: CardAuthorized
        CardAuthorizedEvent cardAuthorized = new CardAuthorizedEvent();
        cardAuthorized.setAccountId(777L);
        cardAuthorized.setAuthorizationId(555L);
        cardAuthorized.setOrderId(123L);
        cardAuthorized.setAmount(new BigDecimal("45.99"));
        
        String payload2 = new com.fasterxml.jackson.databind.ObjectMapper()
            .writeValueAsString(cardAuthorized);
        
        eventHandlers.handleAccountEvent(payload2, "Account#777", "CardAuthorizedEvent");
        
        // Verify authorization status updated
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            Optional<OrderHistoryRecord> record = orderHistoryRepository.findById("123");
            assertTrue(record.isPresent());
            assertEquals("APPROVED", record.get().getAuthorizationStatus());
        });
        
        // Step 3: OrderApproved
        OrderApproved orderApproved = new OrderApproved();
        orderApproved.setOrderId(123L);
        String payload3 = new com.fasterxml.jackson.databind.ObjectMapper()
            .writeValueAsString(orderApproved);
        
        eventHandlers.handleOrderEvent(payload3, "Order#123", "OrderApproved");
        
        // Verify order status updated
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            Optional<OrderHistoryRecord> record = orderHistoryRepository.findById("123");
            assertTrue(record.isPresent());
            assertEquals("APPROVED", record.get().getStatus());
        });
        
        // Step 4: TicketAccepted
        TicketAcceptedEvent ticketAccepted = new TicketAcceptedEvent();
        ticketAccepted.setTicketId(999L);
        ticketAccepted.setOrderId(123L);
        
        String payload4 = new com.fasterxml.jackson.databind.ObjectMapper()
            .writeValueAsString(ticketAccepted);
        
        eventHandlers.handleTicketEvent(payload4, "Ticket#999", "TicketAcceptedEvent");
        
        // Verify ticket status updated
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            Optional<OrderHistoryRecord> record = orderHistoryRepository.findById("123");
            assertTrue(record.isPresent());
            assertEquals("ACCEPTED", record.get().getTicketStatus());
        });
        
        // Step 5: TicketReady
        TicketReadyEvent ticketReady = new TicketReadyEvent();
        ticketReady.setTicketId(999L);
        ticketReady.setOrderId(123L);
        
        String payload5 = new com.fasterxml.jackson.databind.ObjectMapper()
            .writeValueAsString(ticketReady);
        
        eventHandlers.handleTicketEvent(payload5, "Ticket#999", "TicketReadyEvent");
        
        // Verify ticket status updated
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            Optional<OrderHistoryRecord> record = orderHistoryRepository.findById("123");
            assertTrue(record.isPresent());
            assertEquals("READY", record.get().getTicketStatus());
        });
        
        // Step 6: DeliveryPickedUp
        DeliveryPickedUpEvent deliveryPickedUp = new DeliveryPickedUpEvent();
        deliveryPickedUp.setDeliveryId(888L);
        deliveryPickedUp.setOrderId(123L);
        
        String payload6 = new com.fasterxml.jackson.databind.ObjectMapper()
            .writeValueAsString(deliveryPickedUp);
        
        eventHandlers.handleDeliveryEvent(payload6, "Delivery#888", "DeliveryPickedUpEvent");
        
        // Verify delivery status updated
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            Optional<OrderHistoryRecord> record = orderHistoryRepository.findById("123");
            assertTrue(record.isPresent());
            assertEquals("PICKED_UP", record.get().getDeliveryStatus());
        });
        
        // Step 7: DeliveryDelivered
        DeliveryDeliveredEvent deliveryDelivered = new DeliveryDeliveredEvent();
        deliveryDelivered.setDeliveryId(888L);
        deliveryDelivered.setOrderId(123L);
        
        String payload7 = new com.fasterxml.jackson.databind.ObjectMapper()
            .writeValueAsString(deliveryDelivered);
        
        eventHandlers.handleDeliveryEvent(payload7, "Delivery#888", "DeliveryDeliveredEvent");
        
        // Verify final state
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            Optional<OrderHistoryRecord> record = orderHistoryRepository.findById("123");
            assertTrue(record.isPresent());
            OrderHistoryRecord finalRecord = record.get();
            
            assertEquals("APPROVED", finalRecord.getStatus());
            assertEquals("APPROVED", finalRecord.getAuthorizationStatus());
            assertEquals("READY", finalRecord.getTicketStatus());
            assertEquals("DELIVERED", finalRecord.getDeliveryStatus());
            assertEquals(456L, finalRecord.getConsumerId());
            assertEquals(789L, finalRecord.getRestaurantId());
            assertEquals(new BigDecimal("45.99"), finalRecord.getOrderTotal());
        });
        
        // Verify all messages marked as processed
        assertTrue(processedMessageRepository.existsById("Order#123-OrderCreated"));
        assertTrue(processedMessageRepository.existsById("Order#123-OrderApproved"));
        assertTrue(processedMessageRepository.existsById("Account#777-CardAuthorizedEvent"));
        assertTrue(processedMessageRepository.existsById("Ticket#999-TicketAcceptedEvent"));
        assertTrue(processedMessageRepository.existsById("Ticket#999-TicketReadyEvent"));
        assertTrue(processedMessageRepository.existsById("Delivery#888-DeliveryPickedUpEvent"));
        assertTrue(processedMessageRepository.existsById("Delivery#888-DeliveryDeliveredEvent"));
    }
    
    @Test
    void testIdempotentEventProcessing() throws Exception {
        // Create order
        OrderCreated orderCreated = new OrderCreated();
        orderCreated.setOrderId(456L);
        orderCreated.setConsumerId(789L);
        orderCreated.setRestaurantId(111L);
        orderCreated.setStatus("APPROVAL_PENDING");
        orderCreated.setOrderTotal(new Money("25.99"));
        orderCreated.setDeliveryAddress("456 Oak Ave");
        orderCreated.setDeliveryTime(LocalDateTime.now().plusHours(1));
        orderCreated.setCreatedAt(LocalDateTime.now());
        orderCreated.setLineItems(Arrays.asList());
        
        String payload = new com.fasterxml.jackson.databind.ObjectMapper()
            .findAndRegisterModules()
            .writeValueAsString(orderCreated);
        
        // Process event first time
        eventHandlers.handleOrderEvent(payload, "Order#456", "OrderCreated");
        
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            Optional<OrderHistoryRecord> record = orderHistoryRepository.findById("456");
            assertTrue(record.isPresent());
        });
        
        OrderHistoryRecord firstRecord = orderHistoryRepository.findById("456").get();
        LocalDateTime firstUpdatedAt = firstRecord.getUpdatedAt();
        
        // Process same event second time (duplicate)
        eventHandlers.handleOrderEvent(payload, "Order#456", "OrderCreated");
        
        // Wait a bit to ensure no update happens
        Thread.sleep(1000);
        
        // Verify record unchanged
        OrderHistoryRecord secondRecord = orderHistoryRepository.findById("456").get();
        assertEquals(firstUpdatedAt, secondRecord.getUpdatedAt());
        
        // Verify message only processed once
        long processedCount = processedMessageRepository.count();
        assertEquals(1, processedCount);
    }
}
